# Decision register (living log)

IDs D01–D15 follow the Master Blueprint v1.1 / Playbook §10. Labels: **LOCKED**, **OBSERVED**,
**PROVISIONAL**, **OPEN**, **BLOCKED**. Do not upgrade PROVISIONAL → OBSERVED just because code exists.
When resolving an item, record date, alternatives, evidence, rationale, owner and reversal conditions.

## Product / algorithm decisions

| ID | Status | Question / current direction | Evidence so far | Must be addressed in |
|---|---|---|---|---|
| D01 | OBSERVED (top-ranked only) | OpenAlex supplies real works with basic metadata | 30-work audit; 2026-10-17: 3×100 + 1×20 live snapshots, 0 rejected records ([evidence](../evidence/2026-10-17_first-100-candidate-snapshots.md)) | M06 (deeper/non-top samples) |
| D02 | OBSERVED | Broad topics alone too coarse | Primary-topic concentration at 100 works: 57% / 92% / 54% | M07 |
| D03 | OPEN | Are keywords trustworthy subtopics vs noise? | 392–603 distinct keywords per 100 works; no human review yet | M07 human review |
| D04 | OBSERVED (variable) | Missing abstracts need fallback | Abstract present 89 / 77 / 86 of 100 | M04 ✔ fallback implemented; M06 |
| D05 | OBSERVED (variable) | Citation edges incomplete | Internal edges 304 / 70 / 5 per 100; 9–42% of works list no references | M10 |
| D06 | OPEN — **HUMAN DECISION at M05** | Safe merge / version-grouping policy | 16 flagged pairs; mix of true duplicates and same-title different works. Current behavior: evidence only, never merge | **M05** |
| D07 | OPEN — **HUMAN DECISION at M06** | Relevance eligibility floor | None measured; members stored `UNSCREENED` | **M06** |
| D08 | OPEN — **HUMAN DECISION at M07** | Topic vs keyword weights, noise filtering | Confidence scores stored (topics as low as 0.0003) | **M07** |
| D09 | OPEN | Accessibility proxy from metadata? | — | M09/M10 |
| D10 | OPEN | Ordering and stage-naming policy | — | M10 |
| D11 | PROVISIONAL | Candidate pool size (~100–300) | 100 works ≈ 1 request, 3–6 s, USD 0.001/page; config default 100, max 200; depth beyond top-100 untested | **M06** human decision |
| D12 | OPEN | Influence/recency normalization, preference weights | fwci and citation percentile stored when supplied | M09/M14 |
| D13 | OPEN | Does optimizer beat simple ranking? | — | M09/M14 |
| D14 | OPEN | Is synchronous generation fast enough? | Retrieval 3–6 s for 100 candidates (cold); optimizer not built | M13 |
| D15 | LOCKED (untested) | Regeneration from frozen snapshot; immutable revisions | Snapshots freeze raw provider records; revisions not built | M11 |
| S01 | LOCKED | Secret stays local/server-side | Key in `.env` only, Bearer header, never logged; history scans clean | ongoing |
| S02 | PROVISIONAL | Rate limits, retention, anonymous quotas | Not implemented | M13 |
| S03 | OPEN | Deployment host, budget, backups | Not authorized | M15/M16 |

## Project / engineering decisions

| ID | Date | Decision | Rationale | Reversal condition |
|---|---|---|---|---|
| P01 | 2026-10-17 | PostgreSQL host port **5434** (not 5433), bound to 127.0.0.1 | 5433 used by another local project; owner approved | 5433 freed and owner prefers it |
| P02 | 2026-10-17 | Git: small single-purpose branches, verified, `--no-ff` merge, push `main` (pre-approved), no PRs | Owner instruction (overrides playbook §0.5) | Owner asks for PRs/approval per push |
| P03 | 2026-10-17 | Provided planning docs gitignored/untracked (still in pre-2026-10-17 history; history not rewritten) | Owner instruction | Owner asks to publish or scrub history |
| P04 | 2026-10-17 | Ingestion persistence uses parameterized SQL (JdbcClient), not JPA entities | Upsert/batch-heavy writes; Hibernate stays `validate` | Read-side domain needs entity mapping |
| P05 | 2026-10-17 | Each snapshot member stores the exact provider record (`source_record` JSONB) | Reproducibility after cache refresh (playbook M06.5); bounded by the candidate cap (200) and a 512 KB per-record cap (M04) | Storage growth or license concerns → store only feature inputs |
| P06 | 2026-10-17 | OpenAlex retrieval uses `search=` (full-text), default relevance order | Same mode as the audit; recorded per snapshot in `retrieval_params` | M06 comparison with `title_and_abstract.search` |
| P07 | 2026-10-17 | Test fixtures in `backend/src/test/resources/` (not `data/fixtures/`) | Classpath access for tests | Non-test consumers need them |
| P08 | 2026-10-17 | Provider URLs: accept absolute http(s) with host only; encode interior spaces as %20; drop the rest; link preference OA → DOI → landing page (labelled) | Playbook §4.3; real data: 80 of 885 URLs are plain http, 2 OA links had literal spaces, 0 other schemes | UI/security review (M12/M13) requires https-only display |
| P09 | 2026-10-17 | PROVISIONAL normalization bounds: record 512 KB (reject), title 1000 chars (reject), abstract 20000 chars (truncate + flag), 100 stored authors (total kept), response 8 MB | ≥ 4x headroom over measured maxima (41 KB, 172, 4237, 34, 1.2 MB) | Real records hit a limit → re-measure before changing |
