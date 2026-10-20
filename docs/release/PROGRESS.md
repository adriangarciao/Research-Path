# Release progress log

Format per milestone: status · changed files · why · tests run + outcomes · security review · regression ·
remaining risks · next step. Nothing provisioned or deployed.

## R-H1 — Production configuration and security readiness (code) — 2026-10-20
**Status:** code complete; deployment-side checks remain owner-gated (see "Remaining").

| Change (commit) | Files | Why |
|---|---|---|
| Production profile (ab38f23) | `application-prod.yml`, `ProdProfileTests` | Railway runtime: PG* variables with no credential defaults, `sslmode=require` default, pool 5, `PORT`, graceful shutdown, status-only health, dev API off |
| Trusted client-IP header + spoof test (7c7409f) | `ClientIpProperties`, `ClientIpResolver`, `ApiProtectionFilter`, `scripts/spoof-test.ps1`, tests | Per-client limits behind the edge; only one strict IP literal; never `X-Forwarded-For`; release gate script |
| Exact-origin CORS (77ebe36) | `CorsProperties`, `CorsConfiguration`, `CorsTests` | Vercel UI → Railway API; no wildcards |
| Container image (ddf2217) | `backend/Dockerfile`, `.dockerignore`, CI `image` job | Reproducible Railway build; non-root; pinned bases |
| Vercel config + CSP (8887a91) | `frontend/vercel.json`, `frontend/csp.ts`, `vite.config.ts`, `csp.test.ts` | Static UI hardening; exact API origin in `connect-src` without wildcards |
| e2e profile + guard (53c4adf) | `application-e2e.yml`, `TestProfileGuard`, `scripts/backend.ps1 -E2E` | Test relaxations cannot reach production (L4) |

**Tests run (all passed):** `verify.ps1` after every change (final: 318 backend tests, 31 frontend tests, build,
lint, secret scan); CI green including the new image job; mutation check on the client-IP parser (fails 1 test).
**Live checks:**
- `prod` jar refuses a non-TLS database ("The server does not support SSL"); with TLS lowered for the check it serves
  `{"status":"UP"}` only; dev API and `/actuator/env` 404; no password or key in logs.
- Rate-limit identity on the local backend: trusted header on → one header value is limited (429 on the 11th
  rebuild), a different value is a separate client, `X-Forwarded-For` changes nothing, missing/chained header → 400,
  GET unaffected; header off → rotating `X-Real-IP` ignored (429 on the 11th).
- `spoof-test.ps1`: PASS when identities cannot be forged, FAIL ("DEPLOYMENT BLOCKER") when the header is accepted
  from clients — both verified.
- Cross-site browser (production build on `localhost:4173`, API on `127.0.0.1:8080`): load + rebuild with 0 console
  errors; a non-allowed origin is blocked by CORS.
- Under the Vercel headers + meta CSP: 15-paper rebuild, fonts and links with 0 CSP violations; injected inline
  script, off-list fetch and framing blocked.
- Image: builds (166 MB), runs as uid 999, applies V1–V7 to an empty disposable Postgres, listens on the injected
  `PORT`, heap 576 MB of a 768 MB limit, no secrets in logs, graceful SIGTERM shutdown; test containers removed.
- Live e2e suite against `backend.ps1 -E2E`: 7 passed (5 mobile duplicates skipped by design).

**Security review:** no secrets added (history scan clean, 149 commits); new inputs (client-IP header, CORS
origins, `VITE_API_BASE_URL`) are strictly validated and fail closed.
**Regression:** all previous suites green; behaviour unchanged when the new settings are unset (local/test).
**Remaining (owner-gated):** Railway Postgres TLS on the private network, backups + restore test, live spoof test
(G3), CORS with the real Vercel origin, CSP with the real API origin, spend caps. Deploy overlap on Railway to be
observed.
**Next:** H2 — durable OpenAlex spending cap in PostgreSQL.

## R-H2 — Durable OpenAlex spending cap (single instance) — 2026-10-20
**Status:** complete.

**Changed:** V8 `provider_call_budget`; `CallBudget`, `JdbcCallBudget`, `ProviderBudgetProperties`,
`ProviderBudgetExhaustedException` (openalex); `OpenAlexClient` reserves one call before every attempt and refunds only
attempts that never connected; `ApiExceptionHandler` maps exhaustion to 503 + `Retry-After`; the in-memory
"new paths per day" counter was removed from `ApiProtectionFilter`/`RateLimitProperties` (replaced, not weakened).
**Why:** the old cap lived in memory and reset on every restart/redeploy.
**Design:** unit = OpenAlex calls (what the provider bills: $0.001 per search call, $1/day free = 1000), retries
included; reset at 00:00 UTC (the provider's reset); default 900/day. Reservation is one `INSERT … ON CONFLICT DO
UPDATE … WHERE calls < limit RETURNING`, so concurrent requests serialize on the row (also across processes) and
cannot share the remaining budget. Counting happens before sending: a crash can only over-count. Refund only for
connect failures/unknown host (never reached the provider); read timeouts stay counted (may have been billed).
**Tests run (all passed):** `JdbcCallBudgetTests` (limit + midnight-UTC reset, 60 concurrent reservations against 25
remaining → exactly 25, restart keeps the count, refunds never negative, disabled mode), `OpenAlexClientBudgetTest`
(every attempt reserves, nothing sent when exhausted, budget running out between retries stops them, connect failure
refunded / read timeout not), `ProviderBudgetApiTests` (503 + Retry-After, nothing stored, refused attempt not
counted, rebuild still works); mutation (no limit guard) fails 3 tests; `verify.ps1` PASS.
**Live (dev backend):** V8 applied; a real new path used 1 call; the count survived a restart; with the limit at
today's count a new path got 503 `Retry-After: 7880` (= seconds to 00:00 UTC), no path/snapshot stored, count
unchanged, rebuild 201.
**Remaining:** per-connection buckets and in-flight de-duplication remain per process (documented; one replica).
**Next:** M7 — bounded request time (30 s backend provider budget, 45 s frontend), measured before finalizing.

## R-M7 — Bounded request time — 2026-10-20
**Status:** implemented and verified; limits reported to the owner for confirmation (R5).

**Measured first (8 fresh topics, real OpenAlex with key, local dev backend and DB):**

| | n | min | p50 | p90 | max |
|---|---|---|---|---|---|
| Raw provider page (per_page 100, same `select`) | 16 | 0.51 s | 0.69 s | 1.17 s | 1.20 s |
| Whole snapshot, 100 candidates (1 page) | 8 | 3.6 s | 4.5 s | 4.8 s | 4.8 s |
| Whole snapshot, 200 candidates (2 pages) | 8 | 7.4 s | 7.9 s | 10.2 s | 10.2 s |

Pages are 0.8–2.0 MB. Provider time is a small share; the rest is normalization, gates and the snapshot transaction.
Conclusion: 30 s leaves ≥ 12× headroom for a normal provider phase (≈ 1–2.4 s) and room for one or two fast retries;
45 s in the UI sits above 30 s + the measured local work (≤ ~6 s at 200).

**Changed:** `ProviderDeadline` (one per generation, shared by every page and retry); `OpenAlexClient` checks it
before each attempt and before each retry wait, starting either only if `wait + connect + read` still fits, so the
provider phase never exceeds the budget and no pointless wait or request happens; `ProviderTimeBudgetExceededException`
→ 503 "Research data provider too slow", `Retry-After: 10`; `researchpath.openalex.generation-budget: 30s` (validated
≥ connect + read at startup); `read-timeout` 30 s → 10 s. Snapshot logs now carry `pages` and `retrievalMs`.
Frontend: every API call times out at 45 s with a retryable "Timed out" message (a person's cancel stays a cancel);
retrying the same search after a retryable failure reuses its idempotency key, so the server returns a path it may
still have finished (or joins the running one) instead of searching again.
**Cancellation:** generation is synchronous (D14); a browser that gives up does not cancel the server. The server
either stores a complete path in one transaction or nothing (the provider phase finishes before any write), so no
partial or corrupted path is possible; the key reuse avoids a second provider search on retry.
**Tests:** `OpenAlexClientDeadlineTest` (retries stop when the next wait + attempt no longer fits; long Retry-After
fails at once without waiting; expired deadline sends and reserves nothing; deadline shared across pages; unchanged
without a deadline; too-short budget rejected; real JDK HTTP stack: the read timeout bounds a trickling body),
`ApiHardeningTests` (503 + Retry-After 10, no internals, existing path and snapshot untouched); frontend client
timeout/cancel/503 tests and an App test for key reuse. All mocks moved to the 4-argument `searchWorks`.
**Regression:** `verify.ps1` PASS (336 backend tests, frontend build/typecheck/tests/lint, secret scan); live e2e 7
passed (5 mobile-only skips, as before).
**Live:** (1) backend pointed at a local stub that accepts and never answers: exactly 2 attempts (each ended by the
10 s read timeout), then 503 "Research data provider too slow" at 21 s with `Retry-After: 10` — a third attempt
(2 s wait + 15 s) no longer fitted; both attempts counted in the daily budget (may have been billed); no snapshot or
path stored. (2) Real OpenAlex: new path 5.2 s (`retrievalMs=1007`); the same request with the same idempotency key
returned the same path in 15 ms with no second provider call.
**Remaining:** owner confirmation of the measured limits (R5). Measurements were taken against a local DB; repeat
one timing on Railway before launch (part of the GO checklist).
**Next:** M3 — informational broader-topic hint for small eligible pools.

## R-R5 — Time limits finalized — 2026-10-20
**Status:** R5 FINALIZED by the owner for the current implementation (30 s provider budget per generation, 10 s read
timeout, 45 s UI timeout, 503 + `Retry-After: 10`, idempotency key kept across retries); hosted validation pending.
Server-side cancellation on browser timeout is deferred and documented.
**Added:** `scripts/hosted-check.ps1` and runbook gate G4 for the two pre-launch requirements: (1) uncached
200-candidate paths on Railway with managed PostgreSQL, timing the complete request; (2) concurrent requests with one
Idempotency-Key produce one path from one retrieval. Runbook migration count corrected to 8 (V8 from H2).
**Dry run (local backend, real OpenAlex, 200 candidates):** complete request 8.1 / 9.3 / 10.2 s (min/p50/max, n=5),
retrieval ≈ 1.3 s of it; 3 concurrent same-key requests → one path, identical snapshot `retrievedAt`, one 201, and a
single "Created snapshot" in the server log (twice, PASS). Unit evidence:
`PathServiceTests.idempotencyKeyReturnsTheFirstResultWithoutRetrievingAgain`.
**Note:** the in-flight join is per process; a second replica would rely on the unique key in the database (one
replica is required anyway, R2).

## R-M3 — Broader-topic suggestion for small pools — 2026-10-20
**Status:** complete. Relevance gate, selection and stored-path immutability unchanged.

**Changed:** `PathDocument.smallPoolHint` adds `SMALL_POOL: only N of M retrieved papers passed the relevance check …
a broader or differently worded topic may give a fuller path` to a new path's `warnings` when fewer than half of the
retrieved works are eligible (N > 0; zero eligible is still the 422). The UI shows such codes in a separate, neutral
"Suggestions" note ("A broader topic may help"), apart from the amber warnings.
**Why this threshold:** across all evaluated dev snapshots, topics keep 84–99% of retrieved works in the eligible
pool; "causal inference in economics" keeps 31% (62/200) and 42% (42/100). A share rather than an absolute count
works for both 100- and 200-candidate retrievals. A small pool is not treated as invalid: the path is built as before.
**Tests:** `PathDocumentTest` (62/200 and 49/100 hint, exactly half and typical pools do not, zero eligible/zero
retrieved do not), `PathServiceTests.aSmallEligiblePoolAddsOnlyABroaderTopicHint` (12 on-topic + 14 gate-rejected works
→ hint present, and papers, stages, coverage and all other warnings identical to the same 12 works without the
off-topic ones), frontend `isHint`/notice and PathView tests (suggestion region separate from warnings; none
without hints). `verify.ps1` PASS (339 backend tests); live e2e 7 passed (5 mobile skips, as before).
**Live (real OpenAlex):** "causal inference in economics" → 201, 42 of 100 eligible, 10 papers, `SMALL_POOL` shown
under Suggestions (checked in light and dark); "coral reef bleaching" → 95 of 100, no suggestion.
**Note:** stored paths are immutable, so the hint appears on paths created from now on and on new revisions.
**Next:** M4 — at most one paper per normalized title, evaluated under P12 before finalizing.

## R-M4 — One paper per title (D18) — 2026-10-20
**Status:** adopted by the owner (option 1 of 3), accepting the measured misses. OptimizerConfig v4.

**Process (P12):** criteria C1–C9 and the conservative title key were committed before any code; 5 fresh
200-candidate pools (compressed sensing, differential privacy, topological insulators, photocatalytic water splitting,
few-shot learning) were frozen before the constraint existed. Run 1 failed C2/C6/C9, so a soft title-repeat penalty
was pre-registered and swept (λ 0.005–0.2) before asking; it could not avoid the trade-off.
**Changed:** `TitleKey` (case, NFC, quotes/dashes, whitespace, edge punctuation only); optional `titleKey` on
`OptimizerCandidate`; `OptimizerConfig.distinctTitles` (default on) and `titleRepeatPenalty` (default 0, evaluation
only); `GreedyOptimizer` treats a selected title as infeasible for later picks and landmark seeds, and explains a
shortfall ("… with distinct titles … left out because a path holds one work per title"); exhaustive search respects
the constraint; `TitleDiversityComparison` + reports in `docs/evaluation/benchmarks/title-diversity*.md`.
**Results:** 19 → 0 repeated titles in 60 selections; quantum "Quantum Error Correction" once per path (was 2–4);
relevance, budgets, landmarks at k = 10 and runtime unchanged; differential privacy coverage −0.031 / redundancy
+0.014; quantum k = 5 lost its only citation link. Guarantee 1/2 under the constraint (1 − 1/e without repeats);
measured worst 0.931 on 80 tiny pools.
**Tests:** `TitleKeyTest`, `TitleConstraintTest` (in-selection replacement, explained shortfall, untitled works,
seeds, determinism, ≥ 1/2 of the constrained optimum, soft penalty), `TitleDiversityComparisonTest` (real pools);
three deliberate pins updated (GNN selection and path fingerprints, optimizer-config version 4). `verify.ps1` PASS;
browser e2e 7 passed (5 mobile skips); live quantum path shows the topic title once.
**Remaining:** near-identical titles (hyphen vs space) still pass by design. Generated benchmark reports regenerated
under v4.
**Next:** release GO/NO-GO checklist and manual Vercel/Railway steps (owner-gated).
