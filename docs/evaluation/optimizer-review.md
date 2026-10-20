# Optimizer review — M09 (decisions D12, D13)

Date: 2026-10-18 · `GreedyOptimizer` v1, `OptimizerConfig` v1 (default additive, β = 4) · Data: committed
datasets in `data/benchmarks/` (offline; same inputs as the baselines) · Generated artifacts:
[benchmark report](benchmarks/baseline-report.md), [sensitivity sweep](benchmarks/optimizer-sweep.md),
[side-by-side lists](benchmarks/optimizer-side-by-side.md) · Reviewer: Claude Code (owner spot-check welcome).

## Objective and guarantee
F(S) = Σ_c w_c · (1 − Π_{p∈S}(1 − s_{p,c})) + (β / k) · Σ_{p∈S} q_p — probabilistic weighted concept coverage
(monotone submodular, diminishing returns per concept) plus a modular, non-negative quality term
(relevance evidence, OpenAlex age/field-normalized citation percentile, recency/age by focus, weak overview cue;
unknown = neutral 0.5). The sum is monotone submodular, so greedy has the (1 − 1/e) guarantee **for this objective**.
Since D18 (2026-10-20) a path holds at most one work per title: the feasible sets form a matroid, for which greedy
guarantees 1/2 of the best title-distinct set (unchanged 1 − 1/e when no title repeats; measured worst ratio 0.931 on
80 tiny pools, see [title-diversity](title-diversity.md)).
Evidence, not assumption: property tests on 200 seeded random pools (monotone, Δ(p|A) ≥ Δ(p|B) for A ⊆ B, F
increments equal recorded gains); exhaustive optimum on 60 tiny pools — greedy worst ratio **0.899**, exactly
optimal in **53/60** (config v1; 51/60 after the D09 change, see M10), never below 0.632; the bound also checked for β = 4 and for quality-weighted coverage.
Removing the diminishing-returns factor fails 5 tests.

## D12 — choosing β (conservative)
Criteria fixed **before** the extended sweep: (1) coverage above the best baseline on every topic and budget;
(2) mean relevance ≥ 0.95; (3) median normalized citation percentile ≥ 0.9; (4) preferences visibly change output.

| Setting | Meets 1 | Medical k=20 relevance | RL k=10 percentile / citations | Recent − Foundational median year (GNN / RL / medical, k=10) |
|---|---|---:|---|---|
| additive β = 0.25 (initial) | yes | 0.88 ✗ | 0.74 / 1 ✗ | 1 / 0 / 0 ✗ |
| additive β = 2 | yes | 0.90 ✗ | 0.94 / 14.5 | 3 / 8 / 0 |
| **additive β = 4 (chosen)** | yes | 0.95 ✓ | 0.99 / 70 ✓ | 3 / 13 / 1 ✓ |
| quality-weighted β = 4 | yes | 0.95 ✓ | 0.99 / 66 ✓ | 4 / 15 / 1 |
| additive β = 8 | yes (medical only +0.07) | 0.99 | 1.00 / 85 | 5 / 15 / 1 |

**Why β = 0.25 was rejected:** it maximized the (circular) coverage metric by chasing niche concepts with barely
cited papers — RL k = 10 median **1 citation**, medical relevance 0.84–0.90 — and all nine preference profiles
produced the same RL selection. **β = 4** is the smallest setting meeting all four criteria; the additive form was
kept over quality-weighted coverage because it is simpler to explain and retains more coverage at similar
quality. The criteria are enforced as regression tests on the committed datasets.

## Results with the chosen default (Intermediate / Balanced)

| Query | k | Optimizer coverage | Best baseline | Δ | Relevance min / mean | Citations median (optimizer vs best baseline) | Year median |
|---|---:|---:|---|---:|---|---|---:|
| graph neural networks | 10 | 0.785 | 0.459 (relevance) | +0.326 | 1.00 / 1.00 | 397 vs 1080 | 2021 |
| | 20 | 1.000 | 0.719 (relevance) | +0.281 | 1.00 / 1.00 | 266 vs 773 | 2021 |
| reinforcement learning in robotics | 10 | 0.747 | 0.412 (citation) | +0.335 | 1.00 / 1.00 | 70 vs 477 | 2021 |
| | 20 | 0.971 | 0.583 (citation) | +0.388 | 1.00 / 1.00 | 51 vs 257 | 2021 |
| AI-assisted medical diagnosis | 10 | 0.684 | 0.546 (relevance) | +0.138 | 1.00 / 1.00 | 16 vs 1 | 2025 |
| | 20 | 0.900 | 0.665 (provider order) | +0.235 | 0.75 / 0.95 | 8.5 vs 1 | 2025 |

Redundancy stays low (Jaccard 0.014–0.083). Runtime 34–136 ms per selection (200 candidates, includes building the
concept model; first call includes JIT warm-up) vs ~2 ms for a sort.

## D13 — does the optimizer beat simple ranking?
**On the coverage proxy, yes, on all three topics and every budget** (+0.13 to +0.43 over the best baseline for that
topic), while relevance stays at the gate floor or above (mean ≥ 0.95) and normalized influence stays high
(median percentile ≥ 0.9). **Caveats:** coverage is the quantity the optimizer maximizes, so this is partly circular
(blueprint §13); its raw-citation medians are lower than the citation baseline by design (it is not a citation
ranker); whether paths built this way help people learn is **unproven** and needs the M14 human review.

## Side-by-side observations (k = 10; see generated lists)
- RL robotics: surveys (incl. sim-to-real), manipulation, navigation/hierarchical RL, assembly, robot soccer — 2 of
  10 shared with the citation baseline. Pick #1 is a 2026 review with 0 citations (percentile 0.45) chosen for
  breadth (9 new concepts): useful breadth, unknown quality.
- RL robotics k = 10/20 select the 2012 **book-chapter version** of the Kober survey rather than the IJRR 2013
  article (they are PROBABLE_VERSION, not merged): a canonical-version preference could be added later.
- **No selection contains two versions of the same work** (checked for all budgets; diminishing returns already
  discourage concept-duplicate papers). The M05 question is answered for now: no version constraint needed.
- GNN k = 15/20 contain "Graph neural networks" (2024 *Nature Reviews* primer) and "Graph Neural Networks" (2022
  book chapter): different works (pair review), not a duplicate.
- Preferences: Recent vs Foundational shift median years by 3 (GNN) and 13 (RL); 161 of 167 medical candidates
  are from 2020 or later (median 2025), so focus has little room there. ~~Experience effects are deliberately small.~~ **Corrected in M10:**
  at β = 4 the v1 experience weights were strong (Beginner 7–10 of 10 overview-marked papers); D09 lowered
  them to 0.02 (config v2), see [sequence-review.md](sequence-review.md).

## Not done (by design)
Local swap/refinement search (optional per playbook), learned weights, and any claim about learning outcomes.
