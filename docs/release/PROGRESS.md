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
