# ResearchPath benchmarks — what the optimizer improves, what it worsens, what is unproven

Date: 2026-10-19 · Code: `main` (GreedyOptimizer v1, OptimizerConfig **v3** = β 4, experience 0.02, **2 landmark
seeds**; ConceptPolicy v1; SequenceBuilder v1) · Reviewer: Claude Code (owner spot-check welcome).

## Short answer
- **Improves:** breadth. On all 10 topics and both budgets, the optimizer's selections cover far more of the
  subtopics found in the examined papers than any simple ranking (+0.11 to +0.35 weighted coverage at k = 10), while
  relevance stays saturated and the median normalized citation percentile stays 0.91–1.00. This holds on 4 held-out
  and 3 fresh validation topics that were never used to choose a weight.
- **Fixed in D16 (2026-10-19):** the first version often left out a topic's **landmark papers** (0 of the 5
  most-cited works for GNN, protein structure and climate adaptation, even at k = 20; AlphaFold 2 missing). With two
  reserved landmark slots, every default path at k = 10 keeps at least 2 of the 5 most-cited works (mean 2.5 across the
  10 topics), and the protein-structure path includes AlphaFold 2. Cost: up to 0.061 coverage.
- **Still worse than a citation ranking on:** raw citation medians (e.g. 80 vs 477 for RL at k = 10) and keeping all
  five landmarks (citation ranking keeps 5/5 by construction). Redundancy is no longer always lower than the best
  baseline (higher on 3 of 10 topics at k = 10).
- **Unproven:** whether these paths help anyone learn. Coverage is the quantity the optimizer maximizes, so the main
  metric is partly circular; there has been no learning study and no independent human panel.

## Method
- **Inputs:** 10 frozen candidate pools in [`data/benchmarks/`](../../data/benchmarks/README.md) (hash-pinned, CC0
  OpenAlex metadata, 2 pages of OpenAlex full-text search = up to 200 works, beyond the provider's top 100):
  3 audit topics (used to choose D12/D09/D16 settings), 4 held-out topics (added in M14, seen by the M14 review),
  3 fresh validation topics (frozen for D16 before any landmark setting was chosen, used only to validate it).
- **Selectors:** the product optimizer (Intermediate/Balanced) and three rankings — relevance evidence, raw
  citations, provider order (M08). Same pool, same budget (k = 5/10/15/20), same eligibility.
- **Metrics** (`SelectionMetrics`, one shared denominator per pool): weighted concept coverage of the eligible pool
  (combined topics + keywords, concept policy v1); relevance evidence; redundancy (mean pairwise Jaccard of concept
  sets); OpenAlex normalized citation percentile; raw citations; years; links; selection time; **landmarks kept** —
  how many of the pool's 5 most-cited works a selection contains.
- **Reproduce:** `.\scripts\benchmark.ps1` (offline, deterministic apart from timings). Generated reports:
  [baseline report](benchmarks/baseline-report.md), [ablation](benchmarks/ablation.md),
  [landmark sweep](benchmarks/landmark-sweep.md), [β sweep](benchmarks/optimizer-sweep.md),
  [experience sweep](benchmarks/experience-sweep.md), [reading paths](benchmarks/reading-paths.md),
  [side by side](benchmarks/optimizer-side-by-side.md).
- **Environment:** Java 21.0.3 (Temurin), Windows 10, 12 CPUs; CI repeats the pinned results on Linux.

## Results: optimizer vs best simple ranking (current default)

"Best baseline" = the ranking with the highest coverage for that pool and budget (it varies).

| Topic (pool) | k | Coverage opt / best baseline | Percentile median | Citations median opt / baseline | Redundancy opt / baseline | Landmarks opt / baseline |
|---|---:|---|---|---|---|---|
| graph neural networks (198) | 10 | **0.724** / 0.459 relevance | 1.00 / 1.00 | 614 / 1080 | 0.012 / 0.032 | 2 / 2 |
| | 20 | **0.969** / 0.719 relevance | 1.00 / 1.00 | 342 / 773 | 0.025 / 0.032 | 2 / 3 |
| reinforcement learning in robotics (200) | 10 | **0.744** / 0.412 citation | 0.99 / 0.99 | 80 / 477 | 0.063 / 0.060 | 3 / 5 |
| | 20 | **0.957** / 0.583 citation | 0.94 / 0.99 | 62 / 257 | 0.045 / 0.048 | 4 / 5 |
| AI-assisted medical diagnosis (167) | 10 | **0.652** / 0.546 relevance | 0.98 / 0.90 | 16 / 1 | 0.044 / 0.026 | 2 / 0 |
| | 20 | **0.876** / 0.665 provider | 0.92 / 0.81 | 9.5 / 1 | 0.042 / 0.044 | 2 / 0 |
| *held-out:* protein structure prediction (200) | 10 | **0.689** / 0.433 relevance | 1.00 / 1.00 | 335 / 1298 | 0.046 / 0.063 | 2 / 2 |
| | 20 | **0.933** / 0.593 citation | 0.98 / 1.00 | 225 / 1211 | 0.047 / 0.025 | 2 / 5 |
| *held-out:* causal inference in economics (62) | 10 | **0.672** / 0.419 relevance | 0.98 / 0.69 | 9.5 / 1 | 0.010 / 0.037 | 3 / 1 |
| | 20 | **0.926** / 0.718 relevance | 0.91 / 0.84 | 9.5 / 1 | 0.012 / 0.040 | 4 / 2 |
| *held-out:* quantum error correction (198) | 10 | **0.820** / 0.471 citation | 0.98 / 1.00 | 195 / 1052 | 0.030 / 0.053 | 2 / 5 |
| | 20 | **0.982** / 0.608 citation | 0.98 / 1.00 | 195 / 757 | 0.037 / 0.078 | 3 / 5 |
| *held-out:* climate adaptation in agriculture (183) | 10 | **0.762** / 0.449 citation | 0.98 / 1.00 | 119 / 748 | 0.039 / 0.048 | 2 / 5 |
| | 20 | **0.947** / 0.565 citation | 0.98 / 1.00 | 166 / 490 | 0.033 / 0.054 | 2 / 5 |
| *fresh:* neural machine translation (199) | 10 | **0.720** / 0.459 relevance | 0.99 / 0.99 | 152 / 437 | 0.062 / 0.029 | 3 / 2 |
| | 20 | **0.957** / 0.675 relevance | 0.99 / 0.99 | 122 / 408 | 0.041 / 0.028 | 3 / 4 |
| *fresh:* CRISPR gene editing (189) | 10 | **0.622** / 0.430 citation | 0.95 / 0.99 | 60 / 121 | 0.029 / 0.032 | 2 / 5 |
| | 20 | **0.869** / 0.600 citation | 0.94 / 0.98 | 48 / 97 | 0.032 / 0.034 | 3 / 5 |
| *fresh:* urban heat island mitigation (194) | 10 | **0.772** / 0.542 relevance | 0.99 / 0.99 | 137 / 315 | 0.035 / 0.036 | 4 / 4 |
| | 20 | **0.970** / 0.729 relevance | 0.98 / 0.99 | 105 / 207 | 0.049 / 0.044 | 5 / 4 |

Relevance evidence is 0.95–1.00 for every selector: after the relevance gate it no longer discriminates. Optimizer
time per selection: about 40–110 ms (200 candidates, includes building the concept model) vs < 5 ms for a sort.

## D16 — keeping landmark papers ([landmark sweep](benchmarks/landmark-sweep.md))
Criteria fixed before any sweep, judged on the 3 audit topics only: mean landmarks at k = 10 ≥ 2 with every topic
≥ 1; coverage loss ≤ 0.05 for every topic and budget; D12 still met.
1. **Landmark share λ** (blend log(1 + citations) / log(1 + pool max) into influence, with β 4–8): **rejected** — no
   setting passed (keeping 2–5 landmarks cost 0.07–0.09 coverage on GNN/RL; medical landmarks lowered relevance).
2. **Landmark seeds** (declared after 1 failed, same criteria): up to L reserved slots (at most max(1, k / 5)) for
   the pool's 5 most-cited works with strong relevance evidence, placed before greedy selection.
   - 1 seed: within the coverage limit everywhere but mean landmarks 1.33 — fails.
   - **2 seeds:** mean 2.33 (every audit topic ≥ 2), D12 met, but coverage loss **0.061** on GNN at k = 10 — fails
     the 0.05 limit by 0.011.
   - Validation (first run of the fresh topics): 2 seeds pass every criterion on all 3 fresh topics (mean landmarks
     3.0 vs 1.67, loss ≤ 0.050); on the held-out topics loss reaches 0.058 (causal economics) and 0.057 (climate).
3. **Owner decision (2026-10-19): adopt 2 seeds**, accepting the limit being exceeded by ≤ 0.011 in 3 of 14
   topic/budget cases. Seeded papers carry the reason "Included as one of the most-cited works among the papers
   examined for this topic (N citations)". Live check: the real "protein structure prediction" path now includes
   AlphaFold 2 (43,416 citations) and the Phyre server paper (4,175).

## Ablation: which parts matter ([full table](benchmarks/ablation.md))
Measured on the earlier default (no seeds) in M14; the conclusions are unchanged by D16:
- **The coverage term is the source of the breadth gain** (quality only loses 0.18–0.47 coverage) and the reason
  landmarks were pushed out (quality only kept up to 4 of 5).
- **The quality term buys influence for a little coverage:** coverage only (β = 0) gains 0.01–0.24 coverage, but the
  median citation percentile drops to 0.52–0.82 and median citations to 1 on causal economics, medical and RL.
- **Influence is the active quality signal** (hiding it equals coverage only on 5 of 7 topics); relevance is
  saturated after the gate.
- **Keywords carry the selection:** keyword-only concepts select the same set as the default on 4 of 7 topics;
  topic-only concepts lose 0.13–0.50 coverage.
- **The Recent focus works only through publication years.**
- **Greedy vs exact** (synthetic pools): optimal in 51 of 60 tiny pools, worst ratio 0.899 (guarantee 0.632); still
  ≥ (1 − 1/e) with the landmark signal (property test). Exhaustive search is never used on real requests.

## Reviewer pass (M14, before D16)
Reviewed: full k = 10 orders for 7 topics, k = 5 orders for 4 held-out topics, Beginner vs Advanced, and 3–5
individual works per topic.
- **Irrelevant papers:** none found among selected works.
- **Landmarks missing:** the main issue then (AlphaFold 2, *The Graph Neural Network Model*, the most-cited climate
  adaptation works); addressed by D16 above. Some canonical works (GCN, GAT, RoseTTAFold) are not in the retrieved
  pools at all — a retrieval limit, not a selection one.
- **Duplicate-looking items:** the quantum pool contains 34 different works titled just "Quantum Error Correction";
  identity policy correctly keeps them distinct, but paths can show two or three (D17, open).
- **Relevance-gate recall:** "causal inference in economics" keeps 62 of 200 candidates; general methods works
  without "economics" in the title are BORDERLINE (D17, open).
- **Sequencing:** orders are chronological where citations are sparse; stage names are accurate year ranges;
  overview detection misses reviews whose titles lack cue words. No misleading claims found.
- **Beginner vs Advanced:** D09 criteria hold on all held-out topics; experience is a small nudge, as disclosed.

## Known biases and limits
- One provider (OpenAlex) and its full-text relevance order; concept labels and citation counts are provider data
  and can be wrong (the GNN pool lists a 2024 branching paper with 5,184 citations among its top 5).
- The main metric (coverage) is the optimizer's own objective; landmarks and citations are proxies too.
- The fresh validation topics have now been seen; future tuning needs new ones.
- Audit pools were frozen under relevance policy v1, the others under v3; comparisons are within a pool only.
- The reviewer is the agent that built the system; an independent human review is still missing.

## Before/after
Before/after numbers for D16 compare the same pools, objective and budgets: see the landmark sweep (unchanged
setting vs 2 seeds) and the table above (current default).
