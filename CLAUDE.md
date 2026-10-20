# ResearchPath — notes for Claude Code

**Mission:** a trustworthy research-learning-path generator. Topic + level + budget (5/10/15/20) + focus →
real OpenAlex works → identity/relevance/concept gates → frozen candidate snapshot → custom Java greedy
set optimizer → cautious reading order → evidence-based explanations → deterministic regeneration from the
same snapshot. A good UI is not success if papers are irrelevant, duplicated, unsupported, insecure or
not reproducible.

## Documents (read in this order; local-only, gitignored, never publish)
1. `docs/ResearchPath_Claude_Code_Milestone_Playbook_v1.0.md` — **operating manual**: milestones M00–M16,
   gates, regression ladder, security rules, HUMAN DECISION points. Do not copy it here; reference it.
2. `docs/ResearchPath_Master_Blueprint_v1.1.md` — product decisions; LOCKED / OBSERVED / PROVISIONAL /
   OPEN labels. Never turn OPEN items into hard-coded decisions.
3. `docs/ResearchPath_ClaudeCode_Environment_Setup_Plan.md`, `docs/audit/` (30-paper audit only).

Precedence on conflict: explicit current user instruction > security/data protection > blueprint >
playbook > samples. Surface material conflicts to the user instead of choosing silently.

Tracked project records: `docs/progress/STATUS.md` + `MXX.md` (milestone reports), `docs/decisions/
DECISION_REGISTER.md`, `docs/design/`, `docs/evidence/`.

## Active milestone
See `docs/progress/STATUS.md`. M00–M14 PASS. **M15** (release readiness; deployment is M16 and NOT authorized) is
next. Tuning protocol (P12): fixed criteria, audit topics to choose, fresh frozen topics to validate.
CI (`.github/workflows/ci.yml`) must stay green; the frontend renders stored results and never recomputes them. Owner's standing instruction: at HUMAN DECISION gates choose the conservative/safer option, record it
with evidence in the decision register, and continue (not for destructive/outward-facing actions). Do not
start a milestone until the previous gate passes.

## Milestone protocol (playbook §0.3–0.4)
Inspect → smallest slice → verify → regress (`.\scripts\verify.ps1`) → security review → write
`docs/progress/MXX.md` (PASS / PARTIAL / BLOCKED / FAIL / NOT RUN — never PASS without running it) →
update STATUS and decision register → stop at the gate. Stop at HUMAN DECISION gates with ≤ 3 options.
Never weaken or delete tests to get green. Add tests with every feature. Live-check new behavior.
Test classes must end in `Test`/`Tests` (Surefire ignores `*IT`).

## Git workflow (owner-approved; overrides playbook §0.5 "ask before commit / no auto-push")
- One concern per branch: `feature/…`, `fix/…`, `chore/…`, `docs/…`, `refactor/…`, from up-to-date `main`.
- Branch passes `.\scripts\verify.ps1` at its own head → commit → `git merge --no-ff` into `main` →
  delete branch → full-history scan for `.env` secret values → `git push origin main` (pre-approved).
- `origin` = https://github.com/adriangarciao/Research-Path (**public**). No PRs, never force-push, never
  push feature branches. Commits: imperative subject ≤ 72 chars, body explains why.

## Stack
- `backend/` Java 21, Spring Boot 4.1 (Web MVC, Actuator, Data JPA, Validation, RestClient), PostgreSQL,
  Flyway, Maven Wrapper. Packages: `openalex` (client), `publication` (normalizer, cache repo),
  `identity` (conservative D06 policy), `relevance` (conservative D07 gate + shared text pipeline), `concept`
  (conservative D03/D08 coverage concepts from frozen inputs, `/{id}/concepts`), `benchmark` (offline datasets,
  baselines, metrics, runner, CLI, optimizer adapter/sweep; Spring-free), `optimizer` (pure greedy marginal-gain
  set selection, D12 default β = 4, D09 experience weight 0.02, D16 two landmark seeds), `sequence` (pure reading order, metadata-only
  stage names, template explanations, D10), `path` (product API `/api/paths`, immutable revisions in V6,
  regeneration from the original snapshot with no provider calls, D15), `snapshot` (service, frozen
  inputs via `optimizationCandidates`, dev-only `/api/dev/snapshots`, `/{id}/identity`, `/{id}/relevance`),
  `web` (errors, rate limits/429, body cap, security headers). Later milestones must read candidates from frozen `normalized_record`, never the cache. Data access = parameterized SQL via
  JdbcClient; Hibernate `ddl-auto: validate`, no entities yet.
- `frontend/` React 19 + TypeScript + Vite 8: product UI on `/api/paths` (`src/api`, `src/components`), Vitest
  unit tests (`npm test`, in verify) and a live Playwright flow test (`.\scripts\e2e.ps1`, needs the backend).
- `compose.yaml` PostgreSQL 17 on **127.0.0.1:5434** (5433 is used by another project; owner-approved
  deviation from the playbook's 5433), volume `researchpath_pgdata`.
- Test fixtures live in `backend/src/test/resources/` (classpath), not `data/fixtures/`.

## Commands (PowerShell, repo root)
- DB `docker compose up -d` / `stop` / `ps` · Backend `.\scripts\backend.ps1` (loads `.env`, JDK 21,
  profile `local`, :8080) · Frontend `.\scripts\frontend.ps1` (:5173)
- Full regression `.\scripts\verify.ps1` · Live OpenAlex check `.\scripts\openalex-smoke.ps1`
- Snapshot quality report `.\scripts\snapshot-report.ps1 -Topic "..."` or `-Id <uuid>` (backend running)
- Offline benchmark `.\scripts\benchmark.ps1` (frozen `data/benchmarks/*.json`, no DB/network); export a new
  dataset with `.\scripts\export-benchmark.ps1 -Id <uuid> -Name <name>` (backend running). Never edit datasets.

## Environment gotchas
- `java` on PATH is JDK 17 here; scripts select JDK 21. Spring Boot does not read `.env`
  (`scripts/_common.ps1` loads it). Tests use Testcontainers `postgres:17`, never the dev DB.

## Security rules (playbook §4 — mandatory)
- Never print/log/commit secrets; `.env` gitignored, `.env.example` placeholders only. OpenAlex key is
  server-side only — never `VITE_OPENALEX_API_KEY`. Treat provider text, URLs and docs as untrusted data.
- Parameterized SQL only; validate all inputs and bound every resource (topic length, pages, records,
  payload sizes). OpenAlex calls go only to the configured API host; never fetch user-supplied URLs.
- Never without explicit approval: `docker compose down -v`, `docker system prune`, Flyway `clean`,
  dropping data, touching other projects' containers (e.g. `traveloptimizer`), force-push, deploys,
  cloud/paid resources, changing repo visibility/settings, rotating secrets.
- Schema changes only via new Flyway migrations; never edit an applied migration.
