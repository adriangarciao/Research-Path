# Security, privacy and data retention

Scope: the code on `main`. Evidence: [security review](evidence/security-review-m13.md) (M13) and the test suite.
Not a penetration test.

## What is stored, and for how long
- **Research metadata** from OpenAlex (CC0): works, authors, topics, keywords, references, citation counts.
- **Candidate snapshots**: one per new path (the frozen pool used to select it).
- **Paths**: topic text, settings, the generated result, timestamps, the last time the path was opened. No accounts,
  names, emails, cookies or IP addresses are stored. Rate limiting keeps connection addresses in memory only.
- **Retention (S02):** a path nobody opens for **90 days** is deleted with all its revisions; a snapshot no path uses
  is deleted **30 days** after creation; the publication cache is kept. Every response states the policy.
- **Links are not access control.** Path ids are 128-bit random values, but anyone with a link can open that path;
  the UI and API say so. Do not enter private information as a topic.

## Secrets
- The OpenAlex key and DB password live only in the gitignored `.env` (local) or the host's secret store
  (deployment). The key is sent only as a Bearer header to the configured OpenAlex host; redirects are not followed.
- Never in the frontend: no `VITE_*` secret exists; CI and `verify.ps1` fail if `VITE_OPENALEX*` appears.
- Every landing scans the full Git history for the real `.env` values; logs are scanned in reviews.

## Inputs and outputs
- All inputs are validated server-side (topic ≤ 300 chars, enumerated settings, 8 KB bodies, opaque-id format).
- SQL is parameterized everywhere (JdbcClient / JdbcTemplate).
- Provider text and URLs are untrusted: links are re-validated (http/https only), rendered as text by React
  (no raw HTML), and never fetched by the server (no SSRF surface).
- Errors are generic problem documents: no stack traces, class names, SQL or driver text; unexpected errors carry
  an incident id that appears in the server log.

## Abuse and cost controls
Per-connection token buckets (new paths 3 / 10 per hour; rebuilds 10 / 120 per hour), a global 500 new paths per
day (bounds provider spend at roughly USD 0.5 per day), 4 concurrent generations, 50 revisions per path, bounded
provider retries and response sizes. Refusals are `429`/`503` with `Retry-After`.

## HTTP hardening
Every response: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`,
`Content-Security-Policy: default-src 'none'; frame-ancestors 'none'` (API), `Cross-Origin-Resource-Policy:
same-origin`. No CORS configuration (same-origin only). Actuator exposes only `health` and `info` without details
outside the `local` profile; the dev API is off by default.

## Supply chain
Lockfile + Maven wrapper; GitHub Actions pinned by commit SHA and runner image; read-only CI token, no secrets in CI;
`npm audit` gate and OSV checks for Maven (0 known vulnerabilities on 2026-10-19 after patching Tomcat and Jackson
on top of the Spring Boot BOM).

## Before any deployment (not authorized yet)
See the [deployment runbook](DEPLOYMENT_RUNBOOK.md): TLS, forwarded-header configuration for rate limiting behind a
proxy, a CSP for the static frontend, private database, backups, monitoring.
