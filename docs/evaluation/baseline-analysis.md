# Baseline analysis — M08 (before any optimizer)

Source: [benchmarks/baseline-report.md](benchmarks/baseline-report.md) and
[benchmarks/baseline-runs.json](benchmarks/baseline-runs.json), generated offline by `.\scripts\benchmark.ps1`
from the committed datasets in `data/benchmarks/` (SHA-256 recorded per run). Reproduced identically (apart from
timings) from a fresh clone of the GitHub repository with no database, Docker or credentials.

Metrics are **candidate-set proxies**: weighted concept coverage is measured against each pool's own concept
universe (`ConceptPolicy` v1, combined), not against "the field", and says nothing about learning outcomes.

## Weighted concept coverage (higher = more of the pool's weighted concepts represented)

| Query | k | RELEVANCE | CITATION | PROVIDER_ORDER |
|---|---:|---:|---:|---:|
| graph neural networks | 5 | 0.264 | 0.130 | 0.264 |
| | 10 | 0.459 | 0.406 | 0.459 |
| | 20 | **0.719** | 0.554 | **0.719** |
| reinforcement learning in robotics | 5 | 0.210 | **0.298** | 0.210 |
| | 10 | 0.320 | **0.412** | 0.320 |
| | 20 | 0.415 | **0.583** | 0.415 |
| AI-assisted medical diagnosis | 5 | **0.356** | 0.178 | **0.356** |
| | 10 | **0.546** | 0.385 | **0.546** |
| | 20 | 0.612 | 0.615 | **0.665** |

## Observations (including the unflattering ones)
1. **RELEVANCE degenerates to PROVIDER_ORDER.** Lexical gate evidence saturates (coverage 1.0, all query terms in
   the title) for almost every GNN and RL candidate, so the provider-rank tie-break decides; results are identical
   for GNN and RL and differ only at k = 20 for medical AI. The relevance evidence is a good *gate*, not a *ranker*.
2. **No baseline dominates coverage.** Provider order wins on GNN and medical AI; citation order wins on RL robotics
   (0.583 vs 0.415 at k = 20). The optimizer must be judged per topic, not on one convenient query.
3. **Citation order trades recency and relevance for influence.** Medical AI: citation picks span 2009–2025 with
   median 190–595 citations but relevance minimum 0.75 (still eligible); provider-order picks are all 2022–2026
   with median 1 citation and full title relevance.
4. **Redundancy is low everywhere** (mean concept-set Jaccard 0.02–0.10): with ≤ 50 concepts and ~2 per paper,
   overlap is rare; concept repeats grow with k (up to 27 for medical AI at k = 20).
5. **Coverage plateaus.** Even k = 20 covers 42–72% of each pool's weighted concepts; part of every universe sits in
   low-support concepts no simple ranking reaches.
6. **Link completeness is 100%** for all selections (every candidate had a safe link); open-access share varies
   0.20–1.00 and favours highly cited works.

## What this establishes for M09
A fixed, reproducible bar per topic and budget. The optimizer must improve **weighted coverage** over the best
baseline *for that topic* while keeping relevance (minimum ≥ the gate floor, mean reported) and not inflating
redundancy; any topic where it does not will be reported. Runtime here is ~2 ms per selection after JIT warm-up
(first selection ~27 ms), excluding retrieval.

> **Update 2026-10-18 (M09):** the generated report in `benchmarks/` now also contains the optimizer, the sensitivity sweep and side-by-side lists; baseline rows are unchanged (pinned). See [optimizer-review.md](optimizer-review.md).
