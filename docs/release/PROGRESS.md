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
