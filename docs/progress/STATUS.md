# Milestone status

Source of truth for gates (Playbook v1.0 §17). Only **verified** results count; "probably done" is not a status.
Last updated: 2026-10-17.

| ID | Milestone | Status | Exit evidence |
|---|---|---|---|
| M00 | Reality check & Git safety | **PASS** (pre-existing, verified 2026-10-17) | [M00.md](M00.md) |
| M01 | Local monorepo, DB, backend, frontend | **PASS** (pre-existing, verified 2026-10-17) | [M01.md](M01.md) |
| M02 | Database model & migrations | **PASS** (verified 2026-10-17) | [M02.md](M02.md), [data contract](../design/data-contract.md) |
| M03 | OpenAlex client & fixtures | **PASS** — LIVE VERIFIED (2026-10-17) | [M03.md](M03.md) |
| M04 | Normalization & quality | **PASS** (verified 2026-10-17) | [M04.md](M04.md) |
| M05 | Deduplication & versioning | **PASS** (2026-10-17) — D06 conservative policy approved by owner; pair labels: owner spot-check requested | [M05.md](M05.md), [pair review](../evaluation/identity-pair-review.md) |
| M06 | Relevance & frozen snapshots | **NEXT — NOT STARTED.** Pre-existing groundwork only: frozen snapshots with exact provider records, 100-candidate live pools. **No relevance gate** (members `UNSCREENED`), no labeled set, **HUMAN DECISION D07/D11** not done | [evidence](../evidence/2026-10-17_first-100-candidate-snapshots.md) |
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
