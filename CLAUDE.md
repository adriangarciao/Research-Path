# ResearchPath — notes for Claude Code

ResearchPath turns a research topic into an ordered, explainable sequence of real academic papers
(OpenAlex data → relevance gate → set-selection optimizer → reading-order builder).

**Authoritative spec:** `docs/ResearchPath_Master_Blueprint_v1.1.md`. Read the relevant section before
making architectural assumptions; respect its LOCKED / OBSERVED / PROVISIONAL / OPEN labels and do not
turn OPEN items into hard-coded decisions. Audit evidence lives in `docs/audit/` (30-paper sample only).

## Stack
- `backend/` — Java 21, Spring Boot 4.1 (Web MVC, Actuator, Data JPA, Validation), PostgreSQL JDBC, Flyway, Maven Wrapper. Package `dev.researchpath`.
- `frontend/` — React 19 + TypeScript + Vite 8, npm (`package-lock.json` committed).
- `compose.yaml` — PostgreSQL 17, host port **5434** (5433 is taken by another project on this machine), named volume `researchpath_pgdata`.
- Modular monolith: modules inside one Spring Boot app, not separate services.

## Current phase
Environment scaffold only. Not yet implemented: publication schema, OpenAlex ingestion, optimizer,
sequencing, real UI. `V1__baseline.sql` is a no-op; real schema starts at V2. Next milestone:
publication data modeling + OpenAlex ingestion (blueprint §4, §6, §14).

## Working rule: verify every step
After each change: run `.\scripts\verify.ps1` (all must PASS), add tests with the feature, and do a live
check of new behavior (e.g. against the dev DB / OpenAlex). Report only results that were actually run.
Test classes must end in `Test`/`Tests` (Surefire ignores `*IT`).

## Working rule: small, focused branches
- One concern per branch, named by type: `feature/…`, `fix/…`, `chore/…`, `docs/…`, `refactor/…`.
- Branch from up-to-date `main`. Plan large work as a sequence of slices; start each slice from the
  `main` that already contains the previous one. Never accumulate a milestone on one long-lived branch.
- Each branch must build and pass `.\scripts\verify.ps1` **at its own head** before merging.
- Merge into `main` with `git merge --no-ff` (keeps each branch visible as a unit), then delete the branch.
- Commits: imperative subject ≤ 72 chars, body explains why. Never commit `.env` or generated output.
- Pushing / opening PRs needs the user's explicit approval (no remote is configured yet).

## Commands (PowerShell, from repo root)
- DB: `docker compose up -d` / `docker compose stop` / `docker compose ps`
- Backend: `.\scripts\backend.ps1` (loads `.env`, forces JDK 21, profile `local`, port 8080)
- Backend tests: `.\scripts\test-backend.ps1` (Testcontainers, needs Docker running)
- Frontend: `.\scripts\frontend.ps1` (port 5173); build/typecheck: `cd frontend; npm run build`; lint: `npm run lint`
- OpenAlex check: `.\scripts\openalex-smoke.ps1`
- Full regression: `.\scripts\verify.ps1`

## Environment gotchas
- `java` on PATH is JDK 17 on this machine; scripts select JDK 21 via JAVA_HOME. Run Maven via the scripts or set JAVA_HOME first.
- Spring Boot does **not** read `.env`; `scripts/_common.ps1` `Import-DotEnv` loads it into the process.
- Tests use Testcontainers `postgres:17`, never the developer database.

## Rules
- Never commit or print secrets. `.env` is gitignored; `.env.example` holds placeholders only.
- `OPENALEX_API_KEY` is server-side only. Never create `VITE_OPENALEX_API_KEY` or expose any secret through a `VITE_*` variable.
- Never run `docker compose down -v`, `docker system prune`, or touch other projects' containers/volumes (e.g. `traveloptimizer`).
- Schema changes only through new Flyway migrations; never edit an applied migration. Hibernate is `ddl-auto: validate`.
- Do not push to GitHub or deploy without explicit approval.
- Don't edit the provided docs in `docs/` unless asked.
