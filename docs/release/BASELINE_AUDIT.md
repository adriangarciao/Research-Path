# Release baseline audit (Milestone 0)

Date: 2026-10-20 · Commit: `main` 82138b0 (tag `v0.9.0-rc.1`), clean working tree, remote
`git@github.com:adriangarciao/Research-Path.git` · Auditor: Claude Code · Plan:
`ResearchPath_Release_and_Deployment_Plan_2026-10-20.md` (local, gitignored).
Nothing was changed in code, provisioned or deployed for this audit.

## 1. Verified facts (plan assumptions checked against the repository)

| Topic | Observed |
|---|---|
| Layout | Monorepo: `backend/` (Spring Boot **4.1.1**, Java **21**, Maven wrapper; Tomcat 11.0.27 and Jackson 3.1.8 pinned over the BOM for CVEs), `frontend/` (Vite 8, React 19, TypeScript 6, npm + `package-lock.json`), `compose.yaml` (local PostgreSQL 17, 127.0.0.1:5434), Flyway V1–V7. |
| Deployment files | **None**: no Dockerfile, `.dockerignore`, `vercel.json`, `railway.json`/`toml`, Procfile. |
| Profiles | `application.yml` (base) and `application-local.yml` (dev DB, dev API, detailed health, flyway actuator). **No production profile.** The base file has no datasource, so production must supply one. |
| Port | `server.port: 8080` (hard-coded); Railway injects `PORT` → must become `${PORT:8080}`. |
| Datasource / pool | Only in the local profile (`jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}`); Hikari default pool (10), 5 s connection timeout. Railway provides `PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE` and a non-JDBC `DATABASE_URL` → needs explicit JDBC mapping. |
| Schema | Flyway on start, Hibernate `validate`; V1–V7 additive; no destructive migration; immutability triggers on path tables. Clean migrate on an empty DB was proven in M15. |
| Health | `/actuator/health` (+ `/liveness`, `/readiness` probes), DB included, OpenAlex not included, details `never` outside `local`. Exposure: `health`, `info`. Suitable for a Railway healthcheck. |
| Errors | Problem documents; stack traces/messages never included; generic 500 with incident id; DB outage → 503. |
| Security headers (API) | Every response: nosniff, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `CSP: default-src 'none'; frame-ancestors 'none'`, CORP same-origin. |
| CORS | **None configured** → a Vercel-hosted UI calling a Railway API cross-origin will be blocked today. Preflight must allow `Content-Type` and the custom `Idempotency-Key` header. |
| Rate-limit identity | `ApiProtectionFilter` uses `request.getRemoteAddr()`; `server.forward-headers-strategy` unset (Boot does not detect Railway → effectively none). Behind Railway's edge every client would appear as the edge's address → **all users would share one bucket** (3 new paths at once for the whole internet). Not spoofable today, but unusable in production. |
| H2 state | Per-connection token buckets, the in-flight idempotency map **and the global daily cap (500/day)** are in process memory → reset on restart/redeploy. The DB unique constraint on `reading_path.idempotency_key` already prevents duplicate paths across processes (but not duplicate provider calls). Generation concurrency (4) and revisions per path (50) are enforced per process / in the DB respectively. |
| Provider timeouts (M7) | connect 5 s, read 30 s, 3 attempts, back-off 1 s → 30 s cap, `Retry-After` honoured up to 30 s. Worst case per page ≈ 3 × 35 s + 2 × 30 s ≈ **165 s**; a 200-candidate request uses 2 pages (≈ 330 s). Default (100) uses 1 page. Railway closes requests after 5 minutes without data. KNOWN_ISSUES M7 understated this (~2 min). |
| Frontend → API | `VITE_API_BASE_URL` (public, build-time; empty = same origin) already exists; fetch has **no client timeout** (only a user "Stop waiting"). No router library: one page, state in `?path=…&rev=…` → no SPA rewrites needed on Vercel. |
| Frontend CSP readiness | Built `index.html` has no inline scripts/styles; fonts are bundled (`font-src 'self'`). One React inline `style` (coverage bar width) is applied via the CSSOM, which CSP does not block — to be confirmed in a browser with the production CSP. External links: http(s) only, `noopener noreferrer`. |
| CI | GitHub Actions on push/PR: backend `mvnw clean verify` (Testcontainers), frontend build/lint/test/`npm audit`, secret guards; pinned SHAs and runner; no secrets used. Live OpenAlex smoke and Playwright e2e are local-only. |
| Secrets | `.env` gitignored; `.env.example` placeholders only; history scans clean (135 commits); no `VITE_*` secret. |

## 2. Baseline test log (commands actually run, 2026-10-20)
| Command | Result |
|---|---|
| `.\scripts\verify.ps1` | exit 0 in 178 s — backend 304 tests PASS (Testcontainers), frontend build/typecheck PASS, 27 unit tests PASS, lint PASS, secret scan PASS |
| Generic-title probe (jshell over the 10 frozen datasets, default settings) | see §7 |
| Eligible-pool sizes per frozen dataset | see §6 |
Not run (deliberately): live OpenAlex smoke and Playwright e2e (need the backend and spend provider quota; last run passed on 2026-10-19).

## 3. Runbook vs plan: material difference
`DEPLOYMENT_RUNBOOK.md` (M15) assumes **one reverse proxy serving the UI and `/api` from the same origin** (no CORS). The
release plan targets **Vercel (UI) + Railway (API)**. Two ways to do that:
- **Direct (recommended):** browser → `https://<api>.up.railway.app` with exact-origin CORS. Client identity comes from
  Railway's edge (`X-Real-IP`). One timeout (Railway's).
- **Vercel rewrite proxy:** keeps same-origin, but the API then sees Vercel's egress addresses (rate limits collapse
  unless a Vercel-supplied header is trusted, which is a second trust boundary) and Vercel's proxy timeout applies.
The runbook will be updated to the chosen topology (decision R2).

## 4. H1 — what must change (minimal diff)
| # | Change | Why | Test |
|---|---|---|---|
| H1-a | `application-prod.yml` (new; local/test unchanged): datasource from `PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD` with no defaults (fail fast), Hikari pool 5, `server.port: ${PORT:8080}`, dev API off, health details never, graceful shutdown | Railway runtime; no placeholder credentials | context test with `prod` profile; startup on an empty DB |
| H1-b | Trusted client-IP header (config, default **off**; prod = `X-Real-IP`): single valid IP literal only, otherwise connection address; never `X-Forwarded-For`/`Forwarded` | Rate limits per real client behind Railway's edge without accepting client-chosen identities | unit tests: forged/chained/malformed headers, off by default; **live spoof test on Railway is a GO gate** (rotating `X-Real-IP` must still hit 429) |
| H1-c | CORS: exact allowed origins from config (default none), methods GET/POST, headers `Content-Type`, `Idempotency-Key`; no credentials | Vercel UI → Railway API | MockMvc: allowed origin passes preflight, other origins get no ACAO header |
| H1-d | Backend `Dockerfile` (multi-stage, JDK build → JRE 21 runtime, non-root, container-aware heap) + `.dockerignore` | Reproducible Railway build from `/backend` | local `docker build` + run against a disposable DB + health |
| H1-e | `frontend/vercel.json`: CSP (`default-src 'self'; connect-src 'self' <API origin>; img-src 'self' data:; font-src 'self'; style-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'`), nosniff, Referrer-Policy, Permissions-Policy, HSTS | Static UI hardening | local `vite preview` with the same headers + Playwright (no CSP violations, flows pass) |
| H1-f | Durable global daily cap (count snapshots created in the last 24 h from the DB) | Restart/redeploy must not reset the spend cap; also correct with overlapping deploys | Testcontainers test across a "restart" (new filter instance) |
| H1-g | Runbook + `.env.example` for production variables, a test-only profile for the relaxed e2e burst (L4) | Operator clarity; test relaxations never reach production | review; e2e against local profile |
Owner-gated (not code): Railway/Vercel projects, private DB, backups **with a restore test**, spend caps, live spoof test.

## 5. H2 — single-instance constraint
Keep one replica (Railway scales horizontally only when replicas are added manually). With H1-f the spend cap becomes
durable; per-connection buckets and in-flight de-duplication stay per process and reset on restart — acceptable for
v1 because the DB unique key prevents duplicate paths and the durable cap bounds spend. Document it; deploy overlap
is not documented by Railway → observe during the first deploy.

## 6. M3 — relevance-gate recall: not a release blocker
Eligible pools: 9 of 10 frozen topics keep 167–200 of 200 candidates; only "causal inference in economics" keeps 62
(129 BORDERLINE: general causal-inference works without "economics" in the title; 9 TANGENTIAL via aspect cues).
Relaxing the gate would admit general-methods works for every "X in Y" topic and needs a labelled set first (D17).
**Proposal:** no gate change for release; when fewer than ~100 candidates are eligible, show a hint ("only N of M
papers matched all parts of your topic; a broader topic may give a fuller path"). Low risk, no selection change.

## 7. M4 — same-titled works: worth a narrow fix
Works whose normalized title equals the topic, in default paths:
| Topic | In pool | k = 5 | k = 10 | k = 20 |
|---|---:|---:|---:|---:|
| quantum error correction | 34 | **3** | **4** | **4** |
| protein structure prediction | 31 | 0 | 0 | 1 |
| graph neural networks | 3 | 1 | 0 | 2 |
| CRISPR gene editing | 2 | 0 | 1 | 1 |
| neural machine translation | 4 | 0 | 0 | 1 |
| 5 other topics | 0–1 | 0 | 0 | 0 |
They are distinct works (different DOIs, authors, years, publishers; source names are generic, e.g. "Springer eBooks"),
so merging them would be wrong. **Proposal (needs your approval; changes selections):** at most **one** selected work
per normalized title within a path (a selection constraint, like the identity policy), plus showing work type, year and
publisher so remaining items read as different works. Judge it under P12 before adopting: fixed criteria on audit
topics (coverage loss ≤ 0.02, D12 and D16 criteria unchanged), then check on validation topics; re-pin fingerprints.

## 8. M7 — bounded synchronous requests
**Proposal:** an overall provider budget per new path (candidate 30 s; confirm by measurement), read timeout 15 s,
retry only if the remaining budget allows (including `Retry-After`), then a controlled `503 Retry-After`; frontend
abort at 45 s with a retryable message (the previous path stays visible on regeneration — already true). No async
queue. Tests with a slow/429 provider stub; measure normal latency stays ≈ 5–6 s.

## 9. Remaining unknowns (cannot be resolved locally)
Whether Railway's edge overwrites a client-supplied `X-Real-IP`; Railway deploy overlap; Railway Postgres internal TLS
requirement and memory/CPU for the chosen plan; Vercel plan eligibility (personal vs commercial use).
