# Deployment runbook — Vercel (UI) + Railway (API, PostgreSQL)

**Status: nothing provisioned; public deployment NOT authorized.** Every step marked ⛔ needs the owner's explicit
approval (spend, provisioning, publication). Decisions: [release decisions](release/DECISIONS.md).

## Topology (decision R2)
```
Browser ──HTTPS──> Vercel: static React/Vite build (frontend/)
   └──HTTPS, exact-origin CORS──> Railway: Spring Boot API (backend/Dockerfile), ONE replica
                                      ├── private network ──> Railway PostgreSQL 17 (no public TCP proxy)
                                      └── HTTPS, server-side key ──> OpenAlex
```
No Vercel rewrite/proxy for the API (it would hide client IPs and add a second timeout).

## Backend service (Railway) — environment
| Variable | Value | Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | Set in the image; keep it. Never add `local` or `e2e` (`e2e` with `prod` refuses to start). |
| `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` | Railway references, e.g. `${{Postgres.PGHOST}}` | No defaults: startup fails if one is missing. Use the **private** host. |
| `DB_SSLMODE` | `require` (default) | Startup fails if the server does not offer TLS. Confirm on the first deploy; never set `disable` in production. |
| `DB_POOL_SIZE` | `5` (default) | Raise only after measuring. |
| `OPENALEX_API_KEY` | secret | Railway private variable only. |
| `ALLOWED_ORIGINS` | `https://<vercel-production-domain>` | Exact origin(s), comma-separated; wildcards/paths stop startup. |
| `CLIENT_IP_HEADER` | `X-Real-IP` | Enable for the spoof test; **keep only if the spoof test passes** (gate G3). |
| `PORT` | injected by Railway | — |
| `RESEARCHPATH_PROVIDER_BUDGET_DAILY_CALLS` | `900` (default) | OpenAlex calls per UTC day, retries included; keep below the key's allowance (free: 1000). |
| Limits (`RESEARCHPATH_RATE_LIMIT_*`, `RESEARCHPATH_PATHS_*`, `RESEARCHPATH_RETENTION_*`) | defaults | Production values are the defaults in `application.yml`. |
Healthcheck path: `/actuator/health/readiness` (status only; includes the DB, not OpenAlex). Replicas: **1**, no
autoscaling (decision R4).

## Frontend project (Vercel) — settings
Root directory `frontend/`; `frontend/vercel.json` sets install/build/output and the security headers. One build-time
variable: `VITE_API_BASE_URL=https://<railway-api-domain>` (public, not a secret; must be a bare https origin or the
build fails). It is also baked into the page's CSP (`connect-src`). Rebuild after changing it. No SPA rewrites are
needed (one page; state in the query string).

## Order of operations
1. ⛔ Create the Railway project and a PostgreSQL service (record plan and region). Keep it private; enable volume
   backups (daily) **and** run a restore test into a separate service before public traffic (gate G5).
2. ⛔ Create the backend service from this repo with root directory `backend/` (Dockerfile build), set the variables
   above, one replica, healthcheck path, spend alerts/caps approved by the owner.
3. Deploy; check logs for `Successfully applied 8 migrations` (first deploy) and `Started`, then
   `GET /actuator/health/readiness` → 200 `{"status":"UP"}` with no details.
4. **Spoof test (gate G3):** create one path, then `.\scripts\spoof-test.ps1 -ApiBase https://<railway-api-domain>
   -PathId <id>` must print PASS. FAIL is a deployment blocker: unset `CLIENT_IP_HEADER` and stop.
4b. **Timing and idempotency check (gate G4, release R5):** temporarily set `RESEARCHPATH_INGESTION_DEFAULT_CANDIDATES=200`,
   `RESEARCHPATH_RATE_LIMIT_CREATE_BURST=20`, `RESEARCHPATH_RATE_LIMIT_CREATES_PER_HOUR=40`, redeploy, run
   `.\scripts\hosted-check.ps1 -ApiBase https://<railway-api-domain>` (uncached 200-candidate paths, complete request
   time; concurrent requests with one Idempotency-Key → one path, one retrieval, one 201; ≈ 11 OpenAlex calls), then
   **remove the three variables and redeploy**. FAIL or < 10 s headroom below 45 s: report the numbers to the owner
   before changing any limit.
5. ⛔ Create the Vercel project (root `frontend/`), set `VITE_API_BASE_URL`, deploy to the Vercel URL; set
   `ALLOWED_ORIGINS` on Railway to that exact origin and redeploy the API.
6. Cross-service smoke: private window → create, open a source link, rebuild with another budget, reload the link;
   network tab shows only the Railway API origin, no CORS/CSP errors, no secrets; repeat after an API redeploy.
7. ⛔ Public launch only after the owner signs off the GO checklist.

## Rollback
- API: redeploy the previous Railway deployment. Migrations are additive (V1–V7) and must stay backward compatible
  for one release, so a code rollback never needs a schema rollback.
- UI: promote the previous Vercel deployment.
- Data: restore from a tested backup only for data loss (⛔ owner approval); paths are immutable, normal rollbacks
  never touch data.
- Credential exposure: rotate first (⛔), then redeploy, then investigate.

## Operations
Logs never contain request bodies, keys or DB credentials (verified locally). Watch: health, 5xx, 429 counts,
provider 429/5xx, DB size, spend. The retention job runs daily at 03:17 server time. Known limits: per-connection rate limits and
in-flight de-duplication are per process and reset on restart (one replica only); the provider call budget is
durable (PostgreSQL) and shared.
