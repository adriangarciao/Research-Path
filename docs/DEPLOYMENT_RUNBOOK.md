# Deployment runbook (plan only — not executed)

**Status: deployment is NOT authorized (playbook M16, decision S03).** This is the plan a deployment would follow.
Nothing here has been provisioned, purchased or run.

## Target shape
- One container for the backend (Spring Boot fat jar, JDK 21, non-root user, read-only filesystem except `/tmp`).
- The frontend built once (`npm ci && npm run build`) and served as static files by the same reverse proxy that
  forwards `/api` to the backend, so the UI and API stay same-origin (no CORS needed).
- Managed PostgreSQL 17 on a private network, TLS required, automatic daily backups with point-in-time recovery.
- A TLS-terminating reverse proxy / load balancer in front; only ports 443 (and 80 → 443 redirect) are public.

## Configuration (environment, from the host's secret store)
| Variable | Notes |
|---|---|
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Private DB, `sslmode=require` |
| `OPENALEX_API_KEY` | Secret; server-side only |
| `SERVER_FORWARD_HEADERS_STRATEGY=native` | So rate limits see the real client; the proxy must overwrite, not append, `X-Forwarded-For` |
| `RESEARCHPATH_DEV_API_ENABLED=false` | Default; must stay false |
| `RESEARCHPATH_RATE_LIMIT_*`, `RESEARCHPATH_PATHS_*`, `RESEARCHPATH_RETENTION_*` | Defaults are the S02 values; review before launch |
Do not use the `local` profile in deployment (it enables the dev API and detailed health).

## Proxy headers for the static UI
`Content-Security-Policy: default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self';
connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'`, `Strict-Transport-Security:
max-age=31536000; includeSubDomains`, plus the headers the API already sends. Fonts are bundled (no third-party
origins needed).

## Release procedure
1. `main` green in CI; `.\scripts\verify.ps1` PASS locally; tag the release.
2. Build the backend image from the tagged commit; build the frontend from the same commit.
3. Apply migrations with the application's Flyway on start (no manual SQL; never `flyway clean`).
4. Health check: `/actuator/health` must report `UP` (DB included) before traffic is shifted.
5. Smoke: create one path for a known topic, regenerate it, open both revisions.
6. Watch logs for `ERROR` and `429`/`503` rates for the first hour.

## Rollback
- Application: redeploy the previous image tag. Migrations are additive (V1–V7); a rollback must not require
  reversing a migration — new migrations must stay backward compatible for one release.
- Data: restore from the managed backup (point in time) only for data loss; paths are immutable, so normal
  rollbacks never need data changes.

## Operations
- Logs: structured application logs; never log request bodies, the API key or DB credentials.
- Alerts: health DOWN, sustained 5xx, provider 429s (daily OpenAlex budget), DB storage growth.
- Retention job runs daily (03:17 server time); verify its log line weekly.
- Rotate the OpenAlex key and DB password on suspicion of exposure (owner action).

## Open before launch
Hosting provider and budget (S03), domain and TLS certificate, backup retention period, a shared rate-limit store if
more than one backend instance runs, a privacy notice page, and an owner decision to deploy.
