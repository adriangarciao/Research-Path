# ResearchPath

Learn a research topic through an optimized, explainable sequence of real academic papers.
Product and architecture: [docs/ResearchPath_Master_Blueprint_v1.1.md](docs/ResearchPath_Master_Blueprint_v1.1.md).

> **Status: development environment only.** The repository contains a working Spring Boot + PostgreSQL +
> Flyway backend skeleton and a placeholder React page. **Not implemented yet:** publication schema,
> OpenAlex ingestion, relevance gate, optimizer, reading-order builder, path API, real UI, deployment.

## Layout

```text
backend/        Spring Boot 4.1 (Java 21, Maven Wrapper)
frontend/       React 19 + TypeScript + Vite 8
docs/           Blueprint, setup plan, OpenAlex audit (REPORT.md, papers.csv, audit script)
scripts/        Windows PowerShell helpers
compose.yaml    PostgreSQL 17 for local development
.env.example    Template for the private .env (placeholders only)
```

## Prerequisites (Windows)

| Tool | Version used | Check |
|---|---|---|
| JDK | 21 (Temurin) | `& "$env:JAVA_HOME\bin\java" -version` |
| Node.js / npm | 24 LTS / 11 | `node -v; npm -v` |
| Docker Desktop (WSL 2 engine) | 28.x, Compose v2 | `docker info; docker compose version` |
| Git | 2.4x | `git --version` |

Maven does not need to be installed; `backend/mvnw.cmd` downloads it.
If `java -version` shows another JDK first on PATH, that's fine: the scripts select JDK 21 automatically.

## One-time setup

```powershell
.\scripts\setup-env.ps1          # creates private .env with a generated DB password (never printed)
cd frontend; npm ci; cd ..       # optional, scripts\frontend.ps1 does this on first run
```

### Adding your OpenAlex API key (safely)

1. Open `.env` in the repo root (it is gitignored; `git check-ignore .env` confirms).
2. Find the line `OPENALEX_API_KEY=` and paste your key right after `=`, no quotes or spaces.
3. Save. Verify with `.\scripts\openalex-smoke.ps1`, which prints `LIVE API VERIFIED` on success and never prints the key.

Never put the key in `.env.example`, in frontend code, in any `VITE_*` variable, in chat, or in commits.
The key is optional for running the stack; it is only needed for live OpenAlex calls.

## Start / stop

Run each in its own terminal, from the repo root:

```powershell
docker compose up -d             # 1. PostgreSQL 17 on localhost:5434 (`docker compose ps` should show "healthy")
.\scripts\backend.ps1            # 2. Backend on http://localhost:8080   (Ctrl+C to stop)
.\scripts\frontend.ps1           # 3. Frontend on http://localhost:5173  (Ctrl+C to stop)
```

Stop the database (data is kept in volume `researchpath_pgdata`):

```powershell
docker compose stop
```

Do **not** use `docker compose down -v`; `-v` deletes the database volume.

## Ports and settings

| Service | URL / port | Notes |
|---|---|---|
| Frontend (Vite) | http://localhost:5173 | Proxies `/api` and `/actuator` to the backend |
| Backend | http://localhost:8080 | Spring profile `local` |
| PostgreSQL | `localhost:5434` → container 5432 | DB `researchpath`, user `researchpath`, password in `.env` |

5434 is used because 5433 is taken by another local project. To change it, edit `DB_PORT` in `.env`;
Compose and the backend both read it.

## Verify

```powershell
docker compose ps                                              # postgres: healthy
docker compose exec postgres pg_isready -U researchpath -d researchpath
Invoke-RestMethod http://localhost:8080/actuator/health        # status UP, components.db UP
Invoke-RestMethod http://localhost:8080/actuator/flyway        # migration 1 SUCCESS
.\scripts\test-backend.ps1                                     # backend tests (Testcontainers; Docker must be running)
cd frontend; npm run build; npm run lint                       # typecheck + production build + lint
.\scripts\openalex-smoke.ps1                                   # live OpenAlex check (needs key)
```

`/actuator/health` includes a real database check: it returns 503 / `DOWN` within about 5 s if PostgreSQL is stopped.

## Configuration notes

- Docker Compose reads `.env` automatically. **Spring Boot does not.** `scripts\backend.ps1` loads `.env`
  into its own process (nothing is written to your user or system environment).
- Running from an IDE: use profile `local` and set env vars `DB_PASSWORD` (and optionally
  `OPENALEX_API_KEY`) in the run configuration, or install an EnvFile plugin pointing at `.env`.
- Flyway migrations: `backend/src/main/resources/db/migration`. `V1__baseline.sql` is intentionally a
  no-op that only creates Flyway's `flyway_schema_history` table. Add real schema as `V2__...`.
- Hibernate runs with `ddl-auto: validate`; it never creates tables.
- Tests use a throwaway `postgres:17` container (Testcontainers), not your local database.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `DB_PASSWORD must be set in .env` from Compose | Run `.\scripts\setup-env.ps1` |
| Backend: `Could not resolve placeholder 'DB_PASSWORD'` | Start it with `.\scripts\backend.ps1`, not plain `mvnw` |
| Backend: password authentication failed | `.env` password changed after the volume was created. Set it back, or (destroys local data) remove volume `researchpath_pgdata` |
| `JDK 21 not found` / class version errors | Install Temurin 21 and set `JAVA_HOME` to it |
| Port already in use | `Get-NetTCPConnection -LocalPort 5434 -State Listen` to find the owner; change `DB_PORT` in `.env` |
| `docker info` fails / tests error with "Could not find a valid Docker environment" | Start Docker Desktop and wait until the engine is running |
| Maven warnings `HTTP Status: 401` for `flyway-community-db-support` / `red-gate` | Harmless: repositories declared inside Flyway's POM; artifacts resolve from Maven Central |
| `openalex-smoke.ps1`: HTTP 401/403 | Key mistyped or not activated; re-paste it in `.env` |
| `openalex-smoke.ps1`: HTTP 429 | Rate limit or daily budget reached; try later |

## Next milestone

Publication data modeling and OpenAlex ingestion (blueprint §4, §6, §14): canonical identifiers and
possible duplicate versions, topics/keywords with confidence, referenced-work IDs, missing-data flags,
and frozen candidate snapshots.
