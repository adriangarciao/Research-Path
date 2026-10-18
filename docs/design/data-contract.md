# Data contract — normalized publications and candidate snapshots

Status: **current as of Flyway V5 / normalizer v2 / identity policy v1 / relevance policy v1 (2026-10-17)**. Playbook M02 slice 2.
Source of truth in code: `OpenAlexWorkNormalizer`, `NormalizedWork`, `PublicationRepository`,
`V2__publication_and_snapshot.sql`, `V3__normalization_bounds.sql`.

## Evidence the contract is based on
- 3-work real fixture (`backend/src/test/resources/openalex/`), field-by-field.
- 470 cached real works from 6 live snapshots (sizes measured 2026-10-17, see table below).
- The 30-work audit (`docs/audit/`, local only). `papers.csv` was **not** used as a model: it lacks
  abstracts, confidence scores and reference IDs.

## Publication (one row per OpenAlex work; reused across searches)

| Field | Required | Source / rule | Missing means |
|---|---|---|---|
| `openalex_id` | **yes** (unique) | `id`, short form `W\d+` | record **rejected** (`MISSING_OR_INVALID_ID`) |
| `title` | **yes** | `display_name`, else `title`; trimmed; ≤ 1000 chars | rejected (`MISSING_TITLE` / `TITLE_TOO_LONG`) |
| `normalized_title` | derived | case/accent/punctuation-folded title | review key for possible duplicates only |
| `doi` | no | `doi` else `ids.doi`; lowercase, resolver prefix stripped | unknown |
| `publication_year`, `publication_date` | no | integer year; ISO date (invalid → null) | unknown (never defaulted) |
| `work_type`, `language` | no | as supplied | unknown |
| `source_name`, `landing_page_url` | no | `primary_location`; URL must pass `UrlSafety` | unknown / unsafe URL dropped |
| `oa_url`, `is_oa` | no | `open_access`; URL must pass `UrlSafety`; `is_oa` kept even if URL dropped | unknown |
| `abstract_text` | no | rebuilt from `abstract_inverted_index` by position; ≤ 20000 chars (word-boundary cut, `abstract_truncated`) | **not available** — never a rejection |
| `cited_by_count`, `fwci`, `citation_percentile`, `referenced_works_count` | no | as supplied (numbers only) | unknown (not zero) |
| `author_count` | yes | number of named authorships supplied | 0 = no named authors |
| `has_abstract`, `has_topics`, `has_keywords`, `has_references` | yes | derived availability flags | — |
| `references_supplied` | nullable | `referenced_works` present as an array (true even if empty) | NULL only for legacy rows |
| `provider_updated_at` | no | `updated_date` (UTC) | unknown |
| `first_retrieved_at`, `last_retrieved_at` | yes | retrieval timestamps | — |

**Preferred source link** (computed, `NormalizedWork.preferredSourceLink()`): open-access URL → DOI
resolver (`https://doi.org/<doi>`) → landing page, labelled `OPEN_ACCESS` / `DOI` / `LANDING_PAGE`. A link is
a source, not proof of free full text.

## Children
- `publication_author`: ordered, ≤ 100 stored (`author_count` keeps the total); OpenAlex author id optional;
  authorships without a name are skipped (no invented names).
- `concept` + `publication_concept`: kinds `TOPIC` and `KEYWORD` kept **distinct** (same id may exist as
  both). Score = provider confidence in [0, 1]; anything else → NULL (unknown). Primary topic flagged.
  Topic subfield/field/domain retained. No synonym merging yet (M07).
- `publication_reference`: actual referenced work IDs (set semantics; referenced work need not be cached,
  no placeholder rows). Counts are never converted into edges.

## Identity policy (D06 resolved: conservative; `IdentityPolicy` v1)
- Exact identity: OpenAlex work id (unique constraint). Re-ingesting refreshes the row in place.
- `publication_identity_candidate` records **possible** duplicates as evidence — `SAME_DOI`,
  `SAME_NORMALIZED_TITLE` — with both works' id/DOI/title/year/type (cache-level, `review_status` for humans).
- At snapshot creation every pair inside the snapshot is classified and frozen in
  `snapshot_identity_decision` (decision, rule, evidence details): `SAME_WORK` (same DOI + title, or same title
  + same year + shared author + ≤ 1 DOI), `PROBABLE_VERSION`, `DISTINCT`, `UNRESOLVED`.
- Only `SAME_WORK` suppresses: `snapshot_membership.canonical_publication_id` points to the kept member
  (chosen by DOI, open access, safe link, abstract, completeness, lowest id). **Nothing is merged or deleted.**
  `canonical_publication_id` / `candidate_snapshot.identity_policy_version` are NULL for pre-V4 snapshots.
- Missing DOI never matches missing DOI; equal titles alone never suppress.

## Candidate snapshot (frozen pool for one retrieval)
- `candidate_snapshot`: UUID id (unguessable), topic + normalized topic, provider, exact retrieval
  parameters (JSON), requested/received/stored/rejected counts, provider total, **normalizer version**,
  retrieval notes (rejections with reason, within-response duplicates from page drift), created time.
- `snapshot_membership`: provider rank (1-based, unique), provider relevance score, **exact raw provider
  record** (`source_record`, ≤ 512 KB per record), retrieval time, `canonical_publication_id` (identity
  resolution), relevance fields (below).
- `snapshot_identity_decision`: frozen identity decisions for pairs inside the snapshot (see above).
- Relevance (V5): each member stores `relevance_tier` (ELIGIBLE / TANGENTIAL / BORDERLINE / INELIGIBLE),
  `relevance_reason`, `relevance_evidence` (matched / missing terms, aspect cues) and `eligibility_status`
  (ELIGIBLE only for the ELIGIBLE tier); the snapshot stores `relevance_policy_version` and `warnings`.
- **Frozen evaluation inputs (V5):** `normalized_record` is the normalized work exactly as evaluated. Optimization
  candidates = members with `eligibility_status = 'ELIGIBLE'` and `canonical_publication_id = publication_id`,
  read from `normalized_record` — never from the refreshable publication cache. NULL for pre-V5 snapshots.
- Rule: the publication tables are a refreshable cache; a snapshot's evaluation inputs come from its frozen
  `source_record`s, so refreshing the cache never changes an old snapshot.

## Expected sizes (measured on 470 real works, 2026-10-17)

| Quantity | Max observed | Average | Limit enforced |
|---|---:|---:|---|
| Title length | 172 chars | 64 | 1000 (reject) |
| Abstract length | 4,237 chars | 1,248 | 20,000 (truncate + flag) |
| Named authors | 34 | — | 100 stored (total kept) |
| Referenced works | 481 | — | bounded by record size |
| Topics / keywords per work | 3 / 20 | — | bounded by record size |
| Raw record size | 41 KB | 11.6 KB | 512 KB (reject) |
| Search page (100 works) | ~1.2 MB | — | 8 MB response limit |
| Candidates per snapshot | 100 | — | 200 (default 100, PROVISIONAL D11) |

Observed work types: article 242, conference-paper 98, preprint 42, book-chapter 34, review 28,
dissertation 9, book 9, other 4, reference-entry 3, data-paper 1. FWCI absent for 59 of 470.

## Reserved for later milestones (not created yet)
Path, path revision, path item (selection evidence) — M11. Evaluation runs — M08/M14. Relevance labels —
M06. Concept ledger — M07.
