# ResearchPath benchmarks — what the optimizer improves, what it worsens, what is unproven

Date: 2026-10-18 · Milestone M14 · Code: `main` (GreedyOptimizer v1, OptimizerConfig v2, ConceptPolicy v1,
SequenceBuilder v1) · Reviewer: Claude Code (owner spot-check welcome).

## Short answer
- **Improves:** breadth. On all 7 topics and both budgets, the optimizer's selections cover far more of the
  subtopics found in the examined papers than any simple ranking (+0.14 to +0.37 weighted coverage at k = 10), with
  less redundancy and without giving up relevance or field-normalized influence (median citation percentile
  0.91–1.00). This holds on 4 **held-out** topics that were never used to choose a weight.
- **Worsens:** it often leaves out a topic's **landmark papers**. It keeps 0 of the 5 most-cited works for graph
  neural networks, protein structure prediction and climate adaptation, even with 20 papers (the AlphaFold 2 paper,
  43,000 citations, is not chosen for "protein structure prediction"). A citation ranking keeps 5 of 5. Raw
  citation medians of its selections are well below a citation ranking's (e.g. 76 vs 748 for climate adaptation,
  70 vs 477 for RL in robotics at k = 10).
- **Unproven:** whether these paths help anyone learn. Coverage is the quantity the optimizer maximizes, so the main
  metric is partly circular; there has been no learning study and no human panel beyond this review.

## Method
- **Inputs:** 7 frozen candidate pools in [`data/benchmarks/`](../../data/benchmarks/README.md) (hash-pinned, CC0
  OpenAlex metadata, 2 pages of OpenAlex full-text search = up to 200 works, i.e. beyond the provider's top 100).
  3 audit topics (used to choose D12/D09 weights) and 4 held-out topics from biology, economics, physics and
  agriculture.
- **Selectors:** the product optimizer (Intermediate/Balanced) and three rankings — relevance evidence, raw
  citations, provider order (M08). Same pool, same budget (k = 5/10/15/20), same eligibility.
- **Metrics** (`SelectionMetrics`, one shared denominator per pool): weighted concept coverage of the eligible pool
  (combined topics + keywords, concept policy v1); relevance evidence; redundancy (mean pairwise Jaccard of concept
  sets); OpenAlex normalized citation percentile; raw citations; years; links; selection time. Added in M14:
  **landmarks kept** — how many of the pool's 5 most-cited works a selection contains (a reader's "did it keep the
  classics?" check, not something the optimizer optimizes).
- **Reproduce:** `.\scripts\benchmark.ps1` (offline, deterministic apart from timings). Generated reports:
  [baseline report](benchmarks/baseline-report.md), [ablation](benchmarks/ablation.md),
  [β sweep](benchmarks/optimizer-sweep.md), [experience sweep](benchmarks/experience-sweep.md),
  [reading paths](benchmarks/reading-paths.md), [side by side](benchmarks/optimizer-side-by-side.md).
- **Environment:** Java 21.0.3 (Temurin), Windows 10, 12 CPUs; CI repeats the pinned results on Linux.

## Results: optimizer vs best simple ranking

"Best baseline" = the ranking with the highest coverage for that pool and budget (it varies).

| Topic (pool) | k | Coverage opt / best baseline | Percentile median | Citations median opt / baseline | Redundancy opt / baseline | Landmarks opt / baseline |
|---|---:|---|---|---|---|---|
| graph neural networks (198) | 10 | **0.785** / 0.459 relevance | 1.00 / 1.00 | 397 / 1080 | 0.014 / 0.032 | 0 / 2 |
| | 20 | **1.000** / 0.719 relevance | 1.00 / 1.00 | 266 / 773 | 0.032 / 0.032 | 0 / 3 |
| reinforcement learning in robotics (200) | 10 | **0.747** / 0.412 citation | 0.99 / 0.99 | 70 / 477 | 0.042 / 0.060 | 1 / 5 |
| | 20 | **0.971** / 0.583 citation | 0.94 / 0.99 | 51 / 257 | 0.045 / 0.048 | 3 / 5 |
| AI-assisted medical diagnosis (167) | 10 | **0.684** / 0.546 relevance | 0.98 / 0.90 | 16 / 1 | 0.042 / 0.026 | 1 / 0 |
| | 20 | **0.900** / 0.665 provider | 0.92 / 0.81 | 8.5 / 1 | 0.038 / 0.044 | 1 / 0 |
| *held-out:* protein structure prediction (200) | 10 | **0.738** / 0.433 relevance | 0.99 / 1.00 | 239 / 1298 | 0.044 / 0.063 | 0 / 2 |
| | 20 | **0.983** / 0.593 citation | 0.97 / 1.00 | 215 / 1211 | 0.036 / 0.025 | 0 / 5 |
| *held-out:* causal inference in economics (62) | 10 | **0.730** / 0.419 relevance | 0.98 / 0.69 | 9.5 / 1 | 0.013 / 0.037 | 2 / 1 |
| | 20 | **0.926** / 0.718 relevance | 0.91 / 0.84 | 9.5 / 1 | 0.018 / 0.040 | 3 / 2 |
| *held-out:* quantum error correction (198) | 10 | **0.839** / 0.471 citation | 0.98 / 1.00 | 195 / 1052 | 0.032 / 0.053 | 1 / 5 |
| | 20 | **0.982** / 0.608 citation | 0.98 / 1.00 | 136 / 757 | 0.036 / 0.078 | 2 / 5 |
| *held-out:* climate adaptation in agriculture (183) | 10 | **0.819** / 0.449 citation | 0.98 / 1.00 | 76 / 748 | 0.025 / 0.048 | 0 / 5 |
| | 20 | **0.947** / 0.565 citation | 0.98 / 1.00 | 166 / 490 | 0.029 / 0.054 | 0 / 5 |

Relevance evidence is 0.95–1.00 for every selector: after the relevance gate it no longer discriminates. Optimizer
time per selection: 38–112 ms (200 candidates, includes building the concept model) vs < 5 ms for a sort.

## Ablation: which parts matter ([full table](benchmarks/ablation.md))
- **The coverage term is the source of the gain.** Without it (quality only), coverage falls by 0.18–0.47 to about
  baseline level — and landmarks partly come back (up to 4 of 5; still 0 for climate and medical). The coverage term
  is what pushes landmarks out.
- **The quality term buys influence for a little coverage.** Coverage only (β = 0) gains 0.01–0.24 coverage but
  the median citation percentile drops to 0.52–0.82 and median citations to 1 on causal economics, medical and RL
  (0.92 on quantum). Causal economics is a held-out topic, so this supports D12 beyond the tuning data.
- **Influence is the active quality signal.** Hiding the citation percentile gives the same selection as coverage
  only on 5 of 7 topics. Relevance evidence is saturated after the gate, so it barely ranks.
- **Keywords carry the selection.** Keyword-only concepts select exactly the default set on 4 of 7 topics;
  topic-only concepts lose 0.13–0.50 coverage (measured on the combined model). Topics add little beyond keywords.
  A simpler keyword-only model would behave almost the same; the combined model is kept (no measured benefit to
  changing it, and it is the reference for the metric).
- **The Recent focus works only through publication years**, as designed: with years hidden it reverts to the
  default selection on 4 of 7 topics.
- **Greedy vs exact** (synthetic pools, `GreedyOptimizerTest`): optimal in 51 of 60 tiny pools, worst ratio 0.899
  of the exhaustive optimum (guarantee 0.632). Exhaustive search is never used on real requests.

## Reviewer pass (slice 5)
Reviewed: full k = 10 orders for all 7 topics, k = 5 orders for the 4 held-out topics, Beginner vs Advanced paths,
and 3–5 individual works per topic against their titles, years, types and stored concepts.

- **Irrelevant papers:** none found among selected works; the gate excludes off-topic pools well (M06 measured
  0/15 unrelated admitted).
- **Landmarks missing** (main issue): protein structure prediction leads with a 2003 server-evaluation paper and
  includes CASP rounds XIII and XIV but not AlphaFold 2 (in the pool at provider rank 8); graph neural networks
  omits *The Graph Neural Network Model* (2008) and the two most-cited surveys; climate adaptation omits all of its
  5 most-cited works (e.g. *Adaptation options in agriculture to climate change: a typology*, 2002). Some canonical
  works (GCN, GAT, RoseTTAFold) are not in the retrieved pools at all, which is a retrieval limit, not a selection
  one.
- **Duplicate-looking items:** the quantum pool contains 34 different works titled just "Quantum Error Correction"
  (mostly textbook chapters). Identity policy correctly keeps them distinct, but two or three appear per path and look
  like duplicates to a reader; their reasons cite niche keywords (e.g. "depolarizing channel").
- **Relevance-gate recall:** "causal inference in economics" keeps only 62 of 200 candidates; general methods works
  without "economics" in the title (e.g. *Counterfactuals and Causal Inference*, *A Survey on Causal Inference*) are
  BORDERLINE, and labour-economics/governance studies are TANGENTIAL via aspect cues. The resulting path is lexically
  on-topic but includes niche items (a 1990 expert-system chapter, "Causal Inference and Austrian Economics").
- **Sequencing:** orders are chronological where citations are sparse (0–2 links per 10-paper path on held-out
  topics); stage names are accurate year ranges. Overview detection misses reviews whose titles lack cue words
  (e.g. "AI-Driven Deep Learning Techniques in Protein Structure Prediction"). No misleading claims found; reasons
  name stored subtopics only.
- **Beginner vs Advanced:** D09 criteria hold on all held-out topics; Advanced equals Intermediate on all 4 held-out
  pools at k = 10 and Beginner differs by at most one paper — experience is a small nudge, as disclosed.

## Known biases and limits of this evaluation
- One provider (OpenAlex) and its full-text relevance order; concept labels are provider data.
- The main metric (coverage) is the optimizer's own objective; landmarks and citations are proxies too, and provider
  citation counts can be wrong (the GNN pool lists a 2024 branching paper with 5,184 citations among its top 5).
- Weights D12/D09 were chosen on the 3 audit topics; the held-out topics were added after those choices, but this
  review has now seen them, so they are not blind for future tuning.
- Audit pools were frozen under relevance policy v1, held-out pools under v3; comparisons are within a pool only.
- Reviewer is the same agent that built the system; an independent human review is still missing.

## Before/after
No algorithm change was made in M14, so there is no before/after: every number above compares selectors on the same
pool, objective and budget.
