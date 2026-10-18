# Milestone status

Source of truth for gates (Playbook v1.0 §17). Only **verified** results count; "probably done" is not a status.
Last updated: 2026-10-17.

| ID | Milestone | Status | Exit evidence |
|---|---|---|---|
| M00 | Reality check & Git safety | **PASS** (pre-existing, verified 2026-10-17) | [M00.md](M00.md) |
| M01 | Local monorepo, DB, backend, frontend | **PASS** (pre-existing, verified 2026-10-17) | [M01.md](M01.md) |
| M02 | Database model & migrations | IN REVIEW — closing gaps (data contract doc, repository read + roundtrip tests) | — |
| M03 | OpenAlex client & fixtures | IN REVIEW — closing gaps (response size limit, empty/malformed/shape/403 tests); live smoke verified earlier | — |
| M04 | Normalization & quality | IN REVIEW — closing gaps (URL safety + link preference, size bounds, missing-vs-empty references, quality report command) | — |
| M05 | Deduplication & versioning | NOT STARTED as a milestone. Pre-existing groundwork only: exact OpenAlex-ID uniqueness; DOI/title pairs **recorded as evidence, never merged**. Pair review, canonical policy and **HUMAN DECISION D06** not done | — |
| M06 | Relevance & frozen snapshots | NOT STARTED as a milestone. Pre-existing groundwork only: frozen snapshots with exact provider records, 100-candidate live pools. **No relevance gate** (members `UNSCREENED`), no labeled set, **HUMAN DECISION D07/D11** not done | [evidence](../evidence/2026-10-17_first-100-candidate-snapshots.md) |
| M07 | Concept/topic representation | NOT STARTED | — |
| M08 | Ranking baselines | NOT STARTED | — |
| M09 | Marginal-gain optimizer | NOT STARTED | — |
| M10 | Ordering, stages, explanations | NOT STARTED | — |
| M11 | Saved revisions & regeneration | NOT STARTED | — |
| M12 | User-facing flow | NOT STARTED | — |
| M13 | Hardening & performance | NOT STARTED | — |
| M14 | Evaluation & manual quality | NOT STARTED | — |
| M15 | Near-MVP release candidate | NOT STARTED | — |
| M16 | Optional deployment | NOT AUTHORIZED | Separate user approval required |

Decision register: [../decisions/DECISION_REGISTER.md](../decisions/DECISION_REGISTER.md)
