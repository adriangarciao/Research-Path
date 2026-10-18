# Security review — M13 (2026-10-18)

Scope: the backend API, provider client, database setup, frontend bundle and dependencies at `main` after the M13
hardening branch. Reviewer: Claude Code. Method: tests, live probes against the local backend, code search,
dependency audits. Not a penetration test.

## Checklist (playbook M13 security acceptance)

| Check | Result | Evidence |
|---|---|---|
| No plain secrets in repo or history | PASS | `verify.ps1` secret scan + full-history scan on every landing (95+ commits clean) |
| No secrets in current logs | PASS | 62 local log/report files from this session scanned for the `.env` key value: 0 matches (value never printed) |
| No key in the frontend bundle | PASS | `verify.ps1` scans `frontend/dist`; no `VITE_OPENALEX*` anywhere |
| DB bound only locally | PASS | compose publishes `127.0.0.1:5434` only (`docker compose ps`) |
| Admin endpoints restricted | PASS | prod config exposes only `health`, `info` (details `never`); `/actuator/env` and `/heapdump` → 404 live; `/api/dev/**` only with `researchpath.dev-api.enabled` (local profile) |
| User input cannot reach arbitrary hosts (SSRF) | PASS | the only outbound client is OpenAlex at the configured base URL; topic is encoded as a query value (test `topicTextCannotInjectQueryParameters`); provider URLs are display-only and never fetched; **redirects are no longer followed** (was `NORMAL`: the JDK client would re-send the Bearer header to a redirect target) — 3xx is now a non-retried failure (test `redirectIsAFailureNotFollowed`) |
| Malicious paper title displayed as text | PASS | backend returns plain text; React renders text nodes only; tests with `<img onerror>` / `<b>` titles and `javascript:` links (frontend), injection-shaped topics (backend) |
| No stack traces on public errors | PASS | `server.error.include-*` set to never; `FallbackExceptionHandler` returns a generic 500 with an incident id; database failures → generic 503; malformed JSON → generic 400; tests assert no class names, packages, SQL, driver text or secrets in bodies |
| Throttling returns 429 | PASS | live: 11th rebuild in a burst → 429 with `Retry-After`; tests for new-path limit, rebuild limit, global daily cap, bounded client table |
| Docker resources bounded | PASS | `mem_limit 1g`, `cpus 2`, `pids_limit 256`, `no-new-privileges` (verified with `docker inspect`; 7 stored paths intact after recreate) |
| SQL injection | PASS | all SQL is parameterized (`JdbcClient` named parameters); injection-shaped topic stored/handled as data, tables intact |
| CORS | PASS | no CORS configuration: cross-origin requests get no `Access-Control-Allow-Origin` (test); the UI is served same-origin (Vite proxy in dev) |
| Security headers | PASS | every response: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `Cross-Origin-Resource-Policy: same-origin`. The static frontend's own CSP belongs to the hosting setup (M15) |
| Request size | PASS | API bodies capped at 8 KB, also for chunked bodies (413); provider responses capped at 8 MB (M02) |
| File/path operations | PASS | no file access from requests; only the offline `BenchmarkCli`/`DatasetIo` read/write paths given by a developer on the command line |

## Dependency audit

| Ecosystem | Tool | Before | After |
|---|---|---|---|
| npm (all, incl. dev) | `npm audit` | 0 | 0 |
| Maven runtime (96 artifacts) | OSV batch API (`api.osv.dev`, coordinates only) | 3 artifacts: `tomcat-embed-core` 11.0.24 — 3 critical (CVE-2026-65905, -65182, -68525: DIGEST/FORM authentication and access control; the app uses no Tomcat authentication, but patched anyway); `jackson-databind` 3.1.5 — 3 high, 2 moderate (CVE-2026-91777, -68497, -91776, -83557, -19032: DoS and polymorphic-typing issues); `jackson-core` 3.1.5 — 2 high (CVE-2026-89425, -89407: DoS) | **0** |
| Maven test scope (153 artifacts) | OSV | — | 0 |

Fix: Spring Boot 4.1.1 is the newest 4.1.x and still ships the affected versions, so `backend/pom.xml` overrides the
BOM with patch releases on the same lines: `tomcat.version` 11.0.27, `jackson-bom.version` 3.1.8. Full regression
passed and a live path was generated on the patched stack. Remove the overrides when a Boot release includes them.

## Remaining risks (not blockers for local use; must be handled before any deployment)
- Rate limits key on the connection address. Behind a proxy, `server.forward-headers-strategy` must be configured
  so the limiter sees real clients, and the proxy must strip client-supplied `X-Forwarded-For`.
- In-memory limits and the in-flight idempotency map are per instance; several instances need a shared store.
- Retention window for anonymous paths is decided (S02) but not implemented.
- TLS, hosting headers for the static frontend, backups and monitoring belong to M15/M16 (not authorized).
- `postgres:17` floats on the 17 line (security patches arrive on pull); the dev DB is not exposed beyond localhost.
