# Path API (v1)

Base: the API origin (same origin locally; in production the Railway API, called from the Vercel UI with
exact-origin CORS for `/api/paths/**`: methods GET/POST, request headers `Content-Type` and `Idempotency-Key`,
exposed `Location` and `Retry-After`, no credentials). JSON in and out; errors are RFC 9457 problem documents
(`application/problem+json`). There are no accounts: a path id is an unguessable link, not access control.

## Endpoints

| Method | Path | Purpose | Success |
|---|---|---|---|
| `POST` | `/api/paths` | Retrieve candidates for a topic and generate revision 1 | `201` + `Location`; `200` for an idempotent repeat |
| `GET` | `/api/paths/{pathId}` | Latest revision | `200` |
| `GET` | `/api/paths/{pathId}/revisions/{n}` | A specific revision | `200` |
| `POST` | `/api/paths/{pathId}/revisions` | Regenerate from the original snapshot with new settings | `201` + `Location`; `200` if an identical revision exists |

All path responses carry `Cache-Control: no-store`.

### Create
```http
POST /api/paths
Content-Type: application/json
Idempotency-Key: 3f0c2e6e-7d1b-4f0a-9a52-0c1e8b8c1d11    (optional, 8–64 chars [A-Za-z0-9_-])

{ "topic": "graph neural networks", "experience": "intermediate", "budget": 10, "focus": "balanced" }
```
- `topic`: required, 1–300 characters.
- `experience`: `beginner` | `intermediate` (default) | `advanced` (case-insensitive).
- `budget`: `5` | `10` (default) | `15` | `20`.
- `focus`: `balanced` (default) | `foundational` | `recent`.
- With an `Idempotency-Key`, repeating the request returns the first path (`200`) — also while the first is still
  running; reusing the key for a different request is `409`.

### Regenerate
```http
POST /api/paths/{pathId}/revisions
{ "experience": "advanced", "budget": 5, "parentRevision": 1 }
```
Missing settings keep the parent revision's values; `parentRevision` defaults to the latest. No provider request is
made. Limits: 50 revisions per path.

## Response (all four endpoints)

| Field | Meaning |
|---|---|
| `pathId`, `revision`, `parentRevision`, `topic`, `createdAt` | Identity and lineage |
| `options` | `experience`, `focus`, `budget` used |
| `snapshot` | `retrievedAt`, `candidatesRetrieved`, `eligibleCandidates` (the pool selected from) |
| `versions` | `optimizer`, `optimizerConfig`, `conceptPolicy`, `sequence`, `document` |
| `requestedCount`, `selectedCount` | Budget vs papers actually selected |
| `coverage` | `weightedCoverage`, `conceptsCovered`, `conceptsInPool`, `denominator` (text), `concepts[]` (`label`, `kind`, `weight`, `coveredBy` positions), `conceptsNotShown` |
| `stages[]` | `number`, `kind` (`OVERVIEW`/`PERIOD`/`REMAINING`), `label`, `purpose`, `firstPosition`, `lastPosition` |
| `papers[]` | `position`, `stage`, `openalexId`, `title`, `authors` (≤ 5), `authorCount`, `year`, `workType`, `citedByCount`, `link` (http/https only), `linkKind` (`OPEN_ACCESS`/`DOI`/`LANDING_PAGE`), `tags`, `reasons[]` (`code`, `text`), `cites`/`citedBy` (positions in this path), `flags`, `selectionStep` |
| `warnings[]`, `disclosures[]` | `CODE: sentence` strings (e.g. `FEWER_THAN_REQUESTED`, `NO_CITATION_LINKS`, `ORDER_IS_HEURISTIC`) |
| `access` | "Anyone with this link can view this path…" |
| `revisions[]` | All revisions of the path (`revision`, `parentRevision`, `experience`, `focus`, `budget`, `createdAt`) |
| `retention` | `deleteAfterDaysUnviewed`, `notice` — current policy, added at view time (not stored) |

Reason codes: `ADDS_CONCEPTS`, `LANDMARK`, `RELEVANCE`, `INFLUENCE`, `FIT_RECENT`, `FIT_FOUNDATIONAL`, `FIT_BEGINNER`,
`FIT_ADVANCED`, `OVERVIEW_TYPE`, `OVERVIEW_TITLE`. Flags: `CITATIONS_UNKNOWN`, `FEW_CITATIONS`, `INFLUENCE_UNKNOWN`,
`YEAR_UNKNOWN`, `NO_SAFE_LINK`, `ABSTRACT_MISSING`, `NO_SUBJECT_TAGS`. All text is plain text; render it as text.

## Errors

| Status | When | Retry? |
|---|---|---|
| 400 | Invalid settings, topic, idempotency key or JSON | no |
| 404 | Unknown or malformed path id / revision (same answer for both) | no |
| 409 | Revision limit reached; idempotency key reused for another request | no |
| 413 | Body over 8 KB | no |
| 415 / 405 | Wrong content type / method | no |
| 422 | The topic has no eligible papers (nothing stored) | change topic |
| 429 | Rate limit (`Retry-After` seconds) | yes, later |
| 400 *Client address unavailable* | A trusted client-IP header is configured but the request carries no single valid IP (rate-limited POSTs only) | no |
| 502 | Provider request failed (nothing stored) | yes |
| 503 | Provider rate-limited, server busy (`Retry-After`), database unavailable, *Research data provider too slow* (the 30 s provider budget for this request ran out; nothing saved; `Retry-After: 10`), or *Daily limit reached* (today's provider call budget is used up; `Retry-After` = seconds to 00:00 UTC; rebuilds still work) | yes |
| 500 | Unexpected; body has an `incident` id, never internals | yes |

## Limits (defaults, `application.yml`)
Per connection: 3 new paths at once, 10 per hour; 10 rebuilds at once, 120 per hour. Global: a durable budget of 900 OpenAlex calls per UTC day (retries included; counted in PostgreSQL). 4 concurrent generations. Bodies ≤ 8 KB. Retention: paths not opened for 90 days are deleted.

## Development-only API
`/api/dev/snapshots/**` (create/inspect snapshots, relevance/identity/concept diagnostics, dataset export) exists
only when `researchpath.dev-api.enabled=true` (the `local` profile). It must stay disabled in any deployment.
