# Performance — M13 (decision D14)

Date: 2026-10-18 · Local machine (Windows 10, 12 CPUs), backend on JDK 21 with the `local` profile, PostgreSQL 17 in
Docker (1 GB / 2 CPU limit), real OpenAlex for new paths. Rate limits disabled for the measurement only.
Reproduce: start the backend with `RESEARCHPATH_RATE_LIMIT_ENABLED=false`, then `.\scripts\perf.ps1`.

## Latency (sequential client, end-to-end HTTP)

| Operation | n | p50 | p95 | max | Notes |
|---|---:|---:|---:|---:|---|
| New path (cold, provider retrieval of 100 candidates) | 5 | 4.7 s | 6.2 s | 6.2 s | 5 different topics; matches D11 retrieval (3.4–5.8 s) |
| Regenerate, new revision (frozen snapshot) | 35 | 128 ms | 192 ms | 193 ms | every experience × focus × budget combination |
| Regenerate, identical repeat | 20 | 16 ms | 19 ms | 47 ms | answered from the stored revision, no generation |
| Read a stored revision | 200 | 9 ms | 12 ms | 23 ms | |

Regeneration (the playbook's "warm" path) is about **35× faster** than a cold new path; the cold path is dominated by
the provider. Backend working set after the run: 292 MB; CPU time 20.5 s for the whole run.

Concurrency: 12 simultaneous new-revision requests → 4 completed (max 413 ms), 8 refused at once with
503 + `Retry-After: 5` (the M11 bound of 4 concurrent generations). Overload degrades into fast, retryable refusals,
not into queueing.

## Database round trips (`QueryCountTests`, counted at the JDBC boundary)

| Operation | Before | After |
|---|---:|---:|
| Read a stored revision | 4 | 4 |
| Regenerate, 5 papers | 16 | 13 |
| Regenerate, 10 papers | 21 | 13 |
| Identical repeat | 9 | 5 |

Found and fixed: path items were inserted one row at a time (statements grew with the number of papers), and an
identical repeat ran the whole generation before discovering the stored revision. Items are now one batched
statement and repeats check the fingerprint first (the locked check still decides races). The test fails if
statement counts grow with path size again.

## D14 — synchronous or asynchronous generation? (conservative)
**Decision: keep synchronous request/response.** p95 of a cold new path is 6.2 s, regeneration p95 is 0.2 s, and the
UI shows a truthful pending state with "Stop waiting". An asynchronous job system (states, polling, persistence,
retries) would add moving parts without a measured need; the playbook says not to add it for aesthetics.
Conditions to revisit:
- The worst case is bounded but long: up to 3 provider attempts with a 30 s read timeout and back-off can approach
  ~2 minutes before a 502/503. A deployment proxy must allow that, or the attempts/timeouts must be reduced (M15).
- If real traffic shows p95 for new paths above ~15 s, or provider latency grows, revisit with caching or a bounded
  background job.
