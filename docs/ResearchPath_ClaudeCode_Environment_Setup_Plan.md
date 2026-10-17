# ResearchPath — Claude Code Environment & Repository Setup Plan

**Purpose:** A handoff plan for Claude Code to prepare a working *local development environment*, not to build the complete ResearchPath application yet.  
**Platform assumption:** Windows development machine, using PowerShell / VS Code or IntelliJ IDEA. If working in WSL, adapt paths and commands; do not mix a Windows project runtime with an unrelated WSL installation.  
**Primary source of truth:** `docs/ResearchPath_Master_Blueprint_v1.1.md`.  
**Status:** Setup plan, ready to execute. Assumes the user already possesses an OpenAlex API key, but the key must be supplied securely on their computer.

---

## 1. Outcomes to achieve

At the end of this setup task, the developer should be able to:

1. Open one `researchpath` repository with a Spring Boot backend and Vite/React frontend.
2. Start a **local PostgreSQL 17 container** with Docker Compose and a named persistent volume. No separately installed Windows PostgreSQL server is required.
3. Run Spring Boot against the local database, with Flyway and the PostgreSQL JDBC driver configured.
4. Start the React frontend and verify that it runs independently of the backend.
5. Verify the backend's health endpoint and database connectivity, including a clean restart.
6. Supply their already-obtained OpenAlex API key through a **private, gitignored environment file**, without exposing it in frontend code, Git history, terminal output, or docs.
7. Have a short, accurate README explaining how to restart the development stack and what has *not* been implemented.
8. Commit a clean baseline locally, **without automatically pushing to GitHub**.

**Boundary:** This phase is *environment and scaffold only*. No optimizer, academic ingestion pipeline, publication schema, sophisticated frontend, authentication, or deployment is required yet.

---

## 2. What Claude Code can do versus what the user may need to do

| Activity | Owner | Notes |
|---|---|---|
| Inspect existing Java, Node, npm, Git, Docker, Docker Compose, Maven/Gradle, ports | **Claude** | Run version and functionality checks before installing or replacing anything. Prefer project wrappers to system-wide tools. |
| Create folders and project files | **Claude** | Within the approved project directory. |
| Generate Spring Boot project | **Claude** | Use a currently supported stable Spring Boot release compatible with Java 21; include only essential dependencies. |
| Generate React + TypeScript/Vite project | **Claude** | Use current compatible Node LTS and reasonable dependencies. |
| Compose PostgreSQL service, volume, health check | **Claude** | Do not remove existing containers or volumes. |
| Configure Flyway, environment variables, logging, smoke tests | **Claude** | Never put secrets in tracked configuration. |
| Start services, verify DB and backend, fix project issues | **Claude** | Only if necessary installed tools and Docker engine are available. |
| Produce README, `.env.example`, `.gitignore`, `CLAUDE.md` | **Claude** | Verify file exclusions using Git. |
| Install Docker Desktop / enable Windows virtualization / WSL 2 | **You, with guidance from Claude** | Installation may require Windows approvals, interactive license acceptance, reboot, or BIOS settings. **Do not silently modify machine configuration.** |
| Start Docker Desktop and accept interactive prompts | **You** | Claude can attempt permitted commands; GUI and terms require your involvement if prompted. |
| Authenticate Claude Code / GitHub if needed | **You** | Claude may guide login; never ask for your password in chat. |
| Put real OpenAlex key into private environment file | **You** | Paste it locally yourself; never into chat or a committed file. |
| Approve package installation, generated commands and commits | **You** | Keep normal Claude Code permission controls in place. |
| Create GitHub remote / push code | **You authorize** | Claude can run commands *after* you choose the repo/approve push. |

**Important:** If a tool is missing, Claude should identify the missing dependency, provide the official installation route and exact verification command, then continue with other independent tasks. Do not report a failed or skipped verification as passing.

---

## 3. Materials to put in the repository

Suggested root:

```text
researchpath/
  docs/
    ResearchPath_Master_Blueprint_v1.1.md
    audit/
      REPORT.md
      papers.csv
      ResearchPath_Audit_README.md      # optional
  backend/
    ... Spring Boot application ...
  frontend/
    ... Vite React app ...
  scripts/
    ... optional Windows development helpers ...
  compose.yaml
  .env.example
  .env             # PRIVATE: ignored by Git
  .gitignore
  CLAUDE.md
  README.md
```

If available, **preserve** the original `raw/*.json` from the OpenAlex audit under a clearly named *test-fixtures* directory (not as full production data). The CSV alone does not contain complete abstracts, confidence fields, or reference IDs. Keep the raw responses small and review before committing. Existing provided files are not to be edited or overwritten by an unrelated scaffold generator.

Tell Claude to read the master blueprint before making architectural assumptions and to prioritize its **LOCKED / OBSERVED / PROVISIONAL / OPEN** distinctions. The audit is evidence from 30 top-ranked papers, not validation of the full optimizer.

---

## 4. Execution phases and acceptance checks

### Phase A — Preflight and operating environment

**Claude Code responsibilities**

- Identify whether it is running in native Windows PowerShell, Git Bash, or WSL; use compatible commands for that environment.
- Check `java -version`, `node -v`, `npm -v`, `git --version`, `docker --version`, `docker compose version`, and `docker info` (which checks whether the Docker **engine** works, not just the CLI).
- Check Java 21 and a compatible Node LTS, with `where.exe`/`Get-Command` as appropriate when conflicting installations exist.
- Check whether TCP port **5433** is available for the project's database (default choice to avoid interfering with other PostgreSQL projects), plus backend 8080 and frontend 5173; do not kill other programs using them.
- Record missing prerequisites and ask for user action only where required. No automatic system-wide installer execution or administrator escalation without explicit approval.

**Completion check:** A short `PRE-FLIGHT` report with PASS / MISSING / BLOCKED for each prerequisite. If Docker is installed but not running, treat it as blocked until the engine starts.

### Phase B — Repository bootstrap

**Claude Code responsibilities**

- Create or reuse the chosen `researchpath` folder; never overwrite an existing project blindly.
- Initialize Git *only if needed* and create branch-safe initial structure.
- Copy/place the provided master plan and audit into `docs/` when available; otherwise include `docs/README.md` explaining exactly which files to add.
- Create a concise `CLAUDE.md` with product purpose, stack, authoritative blueprint path, no-secret rules, initial phase scope, and run/test commands. Avoid pasting the 80 KB blueprint into `CLAUDE.md`.
- Generate `.gitignore` covering `.env`, `.env.*` except `.env.example`, backend build output, frontend `node_modules/` and build output, IDE files, and OS temporary files. Do not ignore migrations or documentation.

**Completion check:** A clear repository tree; `git status` shows expected tracked sources only, with no secrets or generated dependencies.

### Phase C — PostgreSQL via Docker Compose

**Claude Code responsibilities**

- Create a `postgres:17` Compose service in a project-specific namespace, with database `researchpath`, role `researchpath`, a persistent **named volume**, a health check, and host port default `5433` mapped to container 5432. If occupied, request approval for an alternate port; don't disturb another database.
- Use a local `DB_PASSWORD` sourced from ignored `.env`, and ensure it is **not hardcoded** in tracked Compose or Spring files.
- Either generate a strong local-only password silently into the private `.env` or ask the user to choose one. Never print or commit it.
- Only run `docker compose up -d` when `docker info` succeeds. Wait for readiness, then use `pg_isready` and a small database query to confirm connectivity.
- Verify that `docker compose stop` followed by `docker compose up -d` does not erase data. **Do not run `docker compose down -v`, `docker system prune`, or destructive database commands.**

**Completion check:** PostgreSQL accepts a test connection to `researchpath`; Docker volume is persistent, database logs don't show startup errors.

### Phase D — Spring Boot skeleton

**Claude Code responsibilities**

- Generate a Java 21 Spring Boot application, using Maven Wrapper (`mvnw` / `mvnw.cmd`) or Gradle Wrapper. Maven is preferred for consistency unless existing context dictates otherwise.
- Include Spring Web, Actuator, Spring Data JPA, Validation, PostgreSQL JDBC, Flyway core and its PostgreSQL database support module where required by chosen versions, and Spring Boot testing dependencies. Testcontainers may be added as a test dependency if ready; do not require Docker-in-Docker or an additional PostgreSQL installation.
- Provide `application.yml` plus a clearly documented `local` profile. **Spring Boot does not inherently read the project's `.env` just because Compose does.** Supply a straightforward Windows launch script or documented environment-loading approach so the DB password and optional OpenAlex key reach the Spring process without being printed.
- Configure the application to use `localhost:5433` from Windows (or the approved alternate port). When running tests against Testcontainers, use test-provided connection parameters rather than a developer's local DB.
- Configure Flyway, but don't prematurely design the full publication/citation/path schema. Flyway can be validated with a minimal, harmless initial migration or baseline test as appropriate; document any bootstrap objects created.
- Make health checks real: Actuator should report application readiness, and DB health must be verified separately or via an enabled DB health contributor. Do not treat an HTTP 200 from a stub endpoint alone as proof PostgreSQL works.
- Add a simple context-load/smoke test and run it. Do not implement the optimizer, paper selection, ingestion services, or dummy business endpoints merely to appear complete.

**Completion check:** Backend starts successfully; `GET /actuator/health` responds as configured; Flyway works; DB connection is verified; tests pass and output is summarized.

### Phase E — React scaffold

**Claude Code responsibilities**

- Generate Vite + React + TypeScript under `frontend/`, using supported package versions and reproducible lockfiles.
- Provide a minimal placeholder page titled "ResearchPath — Development Setup" and a configuration for the backend base URL. No fake research recommendation UI and no key exposure.
- Use environment naming consistent with Vite: only `VITE_*` values intentionally meant to be public are exposed. **Never use `VITE_OPENALEX_API_KEY`.**
- Start dev server, run build/typecheck (or documented equivalent), and verify reachable frontend URL.

**Completion check:** Frontend loads locally, compiles cleanly, and does not contain credentials or pretend to have a working research engine.

### Phase F — OpenAlex configuration and smoke check

**Claude Code responsibilities**

- Put `OPENALEX_API_KEY=...` with a non-secret placeholder only in `.env.example`; `.env` remains ignored/private.
- Explain exactly where the user should paste their **already-obtained** key locally. Never ask them to paste it into Claude Code chat, commit history, logs, or frontend settings.
- Document that missing key is acceptable for initial bootstrap and offline fixture tests; live queries cannot be considered validated without a working key.
- Once the private key is supplied, offer a small **server-side** smoke request to OpenAlex (for example, retrieve a single publication/search result), checking HTTP status and a minimal field set. Print only status / number of works / a safe short title; redact all credentials and authorization headers.
- Keep any initial OpenAlex call isolated from future production ingestion logic; full ingestion is a later milestone.

**Completion check:** Either `LIVE API VERIFIED` (after authorized real-key test) or `API KEY PRESENT BUT UNTESTED` / `API KEY NOT PROVIDED`. No false claims.

### Phase G — Final verification and handoff

**Claude Code responsibilities**

- Verify clean start from documented commands: database, backend, frontend, then stop/restart database without loss.
- Run backend tests, frontend build, Git ignore check (`git check-ignore .env`), Git status review, and a light secret scan (without printing values).
- Create README sections: prerequisites, setup, safe key entry, local start/stop, ports, dependency checks, verification, troubleshooting, planned next milestone.
- Provide a final PASS / FAIL / BLOCKED matrix, explicit list of files created, commands actually executed, and items requiring the user's manual action.
- If all checks pass, prepare a local initial commit, but ask before committing; do not auto-push or create cloud resources.

**Completion check:** The repository is a reproducible development foundation. Next phase can begin with publication modeling and OpenAlex ingestion.

---

## 5. Practical local environment conventions

| Setting | Proposed local convention |
|---|---|
| Backend | `http://localhost:8080` |
| Frontend | `http://localhost:5173` |
| PostgreSQL host port | `5433` by default; container remains on `5432` |
| Database name | `researchpath` |
| Database user | `researchpath` |
| Database password | Local generated/entered secret in ignored `.env` |
| API key | `OPENALEX_API_KEY` environment variable, server-side only |
| Java | JDK 21 |
| Database | PostgreSQL 17 in Docker |
| Backend migrations | Flyway |
| Development frontend | React + Vite + TypeScript |

These conventions can change if ports are occupied. Claude must record changes consistently in Compose, local backend config, scripts, and README. `.env.example` contains **placeholder values only**. The actual `.env` must not be tracked.

---

## 6. Manual blockers and exact user actions

**If Docker Desktop or WSL 2 is missing:** You install/enable it and handle any Windows admin permissions, agreement or restart. Ask Claude to re-run the environment check afterward. Do not install PostgreSQL separately if Docker works.

**If GitHub is not authenticated:** You can still work locally. Do not block development on GitHub access; authenticate before pushing remotely.

**If the OpenAlex key has not been pasted:** You edit the local ignored `.env` file and save it. Tell Claude only "key is configured"; the secret itself stays out of chat. Claude should check that the variable is set *without revealing its value*.

**If package installation requires approval:** Approve individual project-scoped installs after reviewing what they'll change. Avoid blanket approval of destructive system operations.

---

## 7. Paste this into Claude Code

> You are preparing the local development environment and monorepo for **ResearchPath**. Read `ResearchPath_ClaudeCode_Environment_Setup_Plan.md` completely, then `docs/ResearchPath_Master_Blueprint_v1.1.md` and the available audit report. Do not build the application features or optimization engine yet.
>
> Work through phases A–G of the setup plan, in order. First inspect my actual OS/shell, installed tools, ports, Git state and Docker daemon; do not assume they work. Explain blockers and stop for my input where interactive installation, Windows/admin permissions, authentication, secret entry or potentially destructive commands are required. Use Java 21 + Spring Boot, React + TypeScript + Vite, PostgreSQL 17 via Docker Compose, and Flyway. Prefer a local, modular monorepo, preserve existing user files, and avoid global installs unless I approve them.
>
> I **already have an OpenAlex API key**. Create a private, gitignored `.env` and a safe `.env.example`; tell me precisely where to paste the real key **myself**. Do not ask me to paste any key or password into the conversation, and never log secrets or expose them to the frontend. If the key is not configured yet, proceed with mock/fixture-based environment setup without claiming live API verification.
>
> Set up and verify Docker PostgreSQL, backend DB/Flyway connectivity, backend health and tests, and frontend build. Be careful that Compose reads `.env` for its own interpolation but Spring Boot must explicitly receive those variables. Use port 5433 for local PostgreSQL unless occupied. Never remove existing Docker containers/volumes, change machine-wide settings, push to GitHub, or deploy anything without approval. Do not implement the paper optimizer, publication schema, or finished UI during this setup task.
>
> At each phase, report what you changed and the actual verification result; fix ordinary project-level errors yourself. At the end, give me a concise PASS/FAIL/BLOCKED table, exact start/stop commands, remaining manual actions, and the next recommended development milestone. Only claim success for tests and services you actually ran.

You can paste the paragraph above if the file is already placed in the project. Otherwise paste the **whole plan** or supply the plan file to Claude Code and ask it to read it.

---

## 8. After setup — the next *separate* request

Once the environment is verified, the next task is **publication data modeling and OpenAlex ingestion**, informed by the October 2026 audit. That task should define canonical identifiers/possible duplicate versions, store topics and keyword confidences and full references, preserve snapshots, and implement missing-metadata fallbacks. **It should not begin during this environment-setup request.**

---

## Notes / official setup references

- Claude Code documentation: https://code.claude.com/docs/en/overview
- Docker Desktop install on Windows: https://docs.docker.com/desktop/setup/install/windows-install/
- Docker Desktop with WSL 2: https://docs.docker.com/desktop/features/wsl/
- Spring Initializr: https://start.spring.io/
- OpenAlex API documentation: https://help.openalex.org/api/

