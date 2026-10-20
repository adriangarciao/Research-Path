# Known issues (release candidate, 2026-10-19)

Severity: **High** = blocks a public launch; **Medium** = visible quality limitation, documented; **Low** = minor.
No known security or data-integrity bug is open.

## High (must be addressed before a public deployment)
| # | Issue | Notes |
|---|---|---|
| H1 | Deployment prerequisites | **Code done** (2026-10-20): prod profile with required TLS, opt-in `X-Real-IP` identity + spoof test, exact-origin CORS, Dockerfile, Vercel CSP/headers, e2e profile guard. **Open (owner-gated):** Railway/Vercel provisioning, DB TLS confirmation, backups + restore test, live spoof test, spend caps — see [runbook](DEPLOYMENT_RUNBOOK.md). |
| H2 | Per-instance controls | **Constrained** (2026-10-20): one Railway replica (decision R4). The global spend cap is now a durable, atomic PostgreSQL budget of OpenAlex calls (V8) that survives restarts and redeploys. Per-connection buckets and in-flight de-duplication stay per process (reset on restart); the DB unique key still prevents duplicate paths. Adding replicas needs a shared limiter first. |

## Medium
| # | Issue | Notes |
|---|---|---|
| M1 | Learning benefit unproven | Coverage is the optimizer's own objective; no learning study, no independent human review ([BENCHMARKS](evaluation/BENCHMARKS.md)). |
| M2 | Landmark seeds cost some breadth | Up to 0.061 coverage; redundancy above the best baseline on 3 of 10 topics; raw citation medians below a citation ranking (D16). |
| M3 | Relevance gate recall on "method in domain" topics | e.g. "causal inference in economics" keeps 62 of 200 candidates (D17). |
| M4 | Generic same-title works | e.g. 34 "Quantum Error Correction" chapters; paths can show two or three (D17). |
| M5 | Reading order is mostly chronological | Citation links among selected papers are sparse (0–7 per path); stage names are year ranges (D10, disclosed). |
| M6 | Provider-limited coverage | Canonical works absent from the retrieved 200 (e.g. GCN, RoseTTAFold) cannot be selected; provider citation counts can be wrong. |
| M7 | Synchronous generation worst case | ~5–6 s normally; currently up to ~165 s per provider page (≈ 330 s at 200 candidates) before an error. A 30 s overall provider budget is being implemented (D14, release R5). |

## Low
| # | Issue | Notes |
|---|---|---|
| L1 | Overview detection is a title/type cue | Reviews without cue words are not marked as overviews (D09). |
| L2 | Accessibility checked by tests and keyboard use only | No automated contrast/axe audit or screen-reader session yet. |
| L3 | Live checks are local | The e2e and OpenAlex smoke tests need the backend and a key; CI runs everything else. |
| L4 | e2e suite needs a relaxed create burst | **Resolved**: test-only `e2e` profile (`scripts\backend.ps1 -E2E`); it refuses to start together with `prod`. |
