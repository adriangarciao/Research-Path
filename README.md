# ResearchPath

Learn a research topic through an optimized, explainable sequence of real academic papers.

The product blueprint, milestone playbook, setup plan and initial OpenAlex audit are private planning
documents kept locally in `docs/` (gitignored). Project records are public: milestone status and reports in
[docs/progress](docs/progress/), decisions in [docs/decisions](docs/decisions/), design notes in
[docs/design](docs/design/), and measured evidence in [docs/evidence](docs/evidence/).

> **Status: playbook milestones M00–M11 PASS** (see [docs/progress/STATUS.md](docs/progress/STATUS.md)).
> Implemented: publication cache (topics/keywords with confidence, referenced-work IDs, missing-data flags,
> bounded and URL-validated), live OpenAlex ingestion, frozen candidate snapshots, conservative duplicate
> handling (obvious duplicates suppressed per snapshot, versions kept and flagged, nothing merged) and a
> metadata-quality report, a conservative relevance gate with frozen evaluation inputs, and a filtered
> topic + keyword concept representation for coverage, an offline benchmark harness with three ranking
> baselines, a custom greedy marginal-gain set optimizer compared with them on committed datasets, and a cautious
> reading-order and stage builder with grounded explanations, exposed through a path API with immutable
> revisions and regeneration from the original snapshot (`POST /api/paths`, `GET /api/paths/{id}`,
> `POST /api/paths/{id}/revisions`).
> **Not implemented yet:** real UI (M12), rate limits (M13), deployment.

## Layout

```text
backend/        Spring Boot 4.1 (Java 21, Maven Wrapper)
frontend/       React 19 + TypeScript + Vite 8
docs/           progress/, decisions/, design/, evidence/ (tracked); planning docs (local only)
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

Run the full regression check after every change (backend tests, frontend build/typecheck/lint, secret scan):

```powershell
.\scripts\verify.ps1
```

Individual checks:

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

## Candidate snapshots (dev API)

Enabled only in the `local` profile (`researchpath.dev-api.enabled`). Requires `OPENALEX_API_KEY` for real use.

```powershell
# Retrieve up to 100 works (max 200) for a topic, store them, and freeze a snapshot
Invoke-RestMethod http://localhost:8080/api/dev/snapshots -Method Post -ContentType 'application/json' `
  -Body '{"topic":"graph neural networks","maxCandidates":100}'
# Re-read a snapshot's summary
Invoke-RestMethod http://localhost:8080/api/dev/snapshots/<id>
# Same, as a readable metadata-quality report (creates with -Topic, re-reads with -Id)
.\scripts\snapshot-report.ps1 -Topic "graph neural networks" -MaxCandidates 100
.\scripts\snapshot-report.ps1 -Id <id>
```

The summary reports metadata availability, distinct topics/keywords, primary-topic concentration,
citation edges between members, and possible-duplicate pairs. These are descriptive counts, not quality
judgments. Rules: missing abstracts/topics are flagged, not rejected; only records with no valid work ID or no
title are rejected; possible duplicates go to `publication_identity_candidate` for review and are never
merged; obvious duplicates (`SAME_WORK`) are suppressed per snapshot by the conservative identity policy,
while probable versions stay as candidates and are flagged (`GET /api/dev/snapshots/<id>/identity`); every
member gets a relevance tier with evidence (`GET /api/dev/snapshots/<id>/relevance`); only ELIGIBLE,
non-duplicate members are optimization candidates. Each snapshot stores the exact
provider record (`snapshot_membership.source_record`), so it stays reproducible after the publication cache
is refreshed. Evidence from the first live runs: [docs/evidence](docs/evidence/).

## Offline benchmarks

Frozen candidate pools for the three audit topics are committed in [data/benchmarks](data/benchmarks/). The
benchmark needs only JDK 21 and this repository (no database, Docker or network):

```powershell
.\scripts\benchmark.ps1        # writes backend\target\benchmarks\baseline-report.md and baseline-runs.json
```

The script also writes the optimizer sensitivity sweep, side-by-side paper lists, sample reading paths and the
experience-weight sweep. Results and analysis: [baselines](docs/evaluation/baseline-analysis.md),
[optimizer](docs/evaluation/optimizer-review.md), [reading paths](docs/evaluation/sequence-review.md).

## Configuration notes

- Docker Compose reads `.env` automatically. **Spring Boot does not.** `scripts\backend.ps1` loads `.env`
  into its own process (nothing is written to your user or system environment).
- Running from an IDE: use profile `local` and set env vars `DB_PASSWORD` (and optionally
  `OPENALEX_API_KEY`) in the run configuration, or install an EnvFile plugin pointing at `.env`.
- Flyway migrations: `backend/src/main/resources/db/migration`. `V1__baseline.sql` is a no-op;
  `V2__publication_and_snapshot.sql` holds the publication/snapshot schema. Never edit an applied migration;
  add `V3__...`.
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

Relevance gate, candidate features, and baselines (blueprint §10, days 5–6). Before that, finish the
Appendix C follow-up: retrieve beyond the top 100 and manually label a varied subset per topic for
relevance, duplicate/version relation, and keyword accuracy (decisions D03, D06, D07).
