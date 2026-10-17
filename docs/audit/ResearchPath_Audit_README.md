# ResearchPath — Automated OpenAlex metadata audit

This is a lightweight **pre-development investigation**, not a new service for the final ResearchPath application.

## What it does

Fetches a small sample of scholarly works from three unrelated queries, inspects whether OpenAlex has the metadata ResearchPath needs for ranking and topic-coverage optimization, and writes a human-readable Markdown report, CSV, JSON summary, and raw responses. It uses the Python standard library—no packages to install.

By default it searches:

1. `graph neural networks`
2. `reinforcement learning in robotics`
3. `AI-assisted medical diagnosis`

It takes the first **10 search-ranked results per topic** (30 total). This is a quick feasibility check, **not** an unbiased sample of all literature.

## Run on your computer

You need Python **3.10 or newer** and internet access.

```bash
python researchpath_openalex_audit.py
```

Outputs are saved to `researchpath_audit_output/`:

- `REPORT.md` — summary, missingness, topic/keyword diversity, automated warnings, and sample titles to inspect.
- `papers.csv` — per-paper metadata and links for viewing or filtering in a spreadsheet.
- `summary.json` — structured metrics, useful for future checks.
- `raw/` — original OpenAlex JSON responses, suitable for repeatable offline analysis.

OpenAlex allows limited anonymous requests. If it refuses or limits access, [get a free OpenAlex API key](https://help.openalex.org/api/authentication/) and set it as an environment variable. Don't commit it to Git.

**Windows PowerShell:**

```powershell
$env:OPENALEX_API_KEY="YOUR_ACTUAL_KEY"
python .\researchpath_openalex_audit.py
```

**macOS / Linux:**

```bash
export OPENALEX_API_KEY="YOUR_ACTUAL_KEY"
python3 researchpath_openalex_audit.py
```

To request 30 papers per topic (90 total):

```bash
python researchpath_openalex_audit.py --per-topic 30
```

To change topics:

```bash
python researchpath_openalex_audit.py --topics "computer vision for autonomous driving" "microplastic pollution" "quantum error correction"
```

To **rerun without spending API requests**, reuse the raw snapshot directory:

```bash
python researchpath_openalex_audit.py --input-dir researchpath_audit_output/raw --output researchpath_audit_recheck
```

## What we'll use to make decisions

- **Metadata completeness:** How often are abstracts, topic labels, keywords, and citation references present?
- **Classification diversity:** Are topic labels too similar across a query? Do keywords add useful distinctions?
- **Topic dominance:** If nearly every paper has the same primary topic, a topic-only optimizer may not meaningfully diversify the reading plan.
- **Citation availability:** How many papers have outgoing references (not whether those are true prerequisites)?
- **Data quality by field:** Are some subjects consistently missing metadata?

This tool does **not** prove papers are relevant, useful, or accessible to beginners. Review 3–5 paper titles and classifications per topic using the report. If keywords are specific and available, they become a promising feature for the optimizer; we still need semantic spot checks.

## How this fits ResearchPath

Use the report to decide whether to build coverage features from OpenAlex topics alone or topics **plus** keywords. Then implement the production ingestion in **Java / Spring Boot**, matching the established project blueprint. This Python script remains an optional audit and evaluation utility; it does not need to run as part of the application.

## Notes on API and privacy

- The script only performs public OpenAlex work-search GET requests and writes local files.
- Results are truncated to the requested `--per-topic` count; no PDF downloading.
- A free API key is read from `OPENALEX_API_KEY` and sent via Authorization header; it is not saved in the outputs.
- Network failures and API rate limits are reported. Retries use a bounded delay.
- Dataset classifications may change as OpenAlex models/data change. Keep the raw snapshots for repeatable comparisons.
