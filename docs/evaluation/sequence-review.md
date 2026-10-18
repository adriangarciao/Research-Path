# Reading-path review — M10 (decisions D09, D10)

Date: 2026-10-18 · `SequenceBuilder` v1 on `GreedyOptimizer` v1 / `OptimizerConfig` v2 · Data: committed datasets in
`data/benchmarks/` (offline) · Generated artifacts: [reading paths](benchmarks/reading-paths.md),
[experience-weight sweep](benchmarks/experience-sweep.md) · Reviewer: Claude Code (owner spot-check welcome).

## What the sequence stage does
It runs **after** selection and never changes the selected set (tested for 3 topics × 4 budgets × 9 preference
profiles; Intermediate optimizer and path fingerprints pinned).
1. Works marked as reviews/surveys by metadata (OpenAlex type `review`, or a survey-like title word) come first.
2. Within that group and within the rest, a selected paper cited by another selected paper is placed before it, as
   **suggestive background, never a stated prerequisite**. Otherwise order is by publication year (unknown last),
   then selection step, then id. A cited paper moves just ahead of its earliest citer instead of pushing other
   papers back. Citation cycles are broken at their earliest paper.
3. Stages: at most 2 (≤ 5 papers), 3 (≤ 10) or 4, each with ≥ 3 papers where possible; cuts move up to two places to
   fall between different years. Names are limited to what metadata supports: **"Overviews and surveys"** and
   **year ranges** ("Papers from 2019–2021"); one remaining group is "Selected papers" / "Further papers".
4. One or two "why it's here" sentences per paper, filled from the optimizer's recorded decision (concepts the paper
   newly added, or its dominant quality factor) and, for overview-marked works, the metadata source ("content not
   verified"). Up to four subject tags from the paper's included concepts.
5. Uncertainty flags (citations unknown or < 5, influence unknown, year unknown, no safe link, abstract missing, no
   subject tags); links re-validated (http/https only); two standing disclosures: the order is heuristic, and no
   difficulty is estimated.

## Observations on the three sample topics

| Topic | Citation links among selected (k = 5 / 10 / 15 / 20) | Cycles | k = 10 stages (Intermediate) |
|---|---|---:|---|
| graph neural networks | 0 / 1 / 3 / 4 | 0 | Overviews and surveys (1) → Papers from 2019–2021 (6) → Papers from 2022 (3) |
| reinforcement learning in robotics | 0 / 2 / 7 / 3 | 0 | Overviews and surveys (5) → Further papers (5) |
| AI-assisted medical diagnosis | 0 / 0 / 0 / 0 | 0 | Papers from 2022–2024 (4) → Papers from 2025 (4) → Papers from 2026 (2) |

- **Citation evidence is sparse**, as the blueprint audit predicted: at most 7 links among 20 selected papers, none
  for medical, and none at k = 5 on any topic. Most of the order is therefore the documented fallback (overview cue
  then chronology), and the path says so (`NO_CITATION_LINKS`).
- Where links exist they behave sensibly. In RL the 2012 Kober survey is read first, and the 2007 robot-soccer paper
  it cites is first among the non-survey papers. In GNN the traffic-forecasting survey (stage 1) cites the 2021
  data-augmentation paper, which stays in its own year stage.
- Year-range stage names are accurate but carry no pedagogy. That is intended (D10): we have no reliable evidence
  for "Methods" or "Applications" groupings.
- RL's default k = 10 path is half overview-marked works, including two 2026 reviews with < 5 citations (flagged
  `FEW_CITATIONS`). That is a selection property (M09), shown honestly here and left for M14 review.
- Every concept named in a sentence or tag is one of the paper's stored concepts (test-enforced on all
  datasets/budgets/profiles). No sentence or stage text claims a prerequisite, dependency or "must read first".

## D09 — is experience fit useful enough to keep? (conservative)
Inspecting the first Beginner and Advanced paths (config v1: Beginner weight 0.15 on overview-marked works,
Advanced 0.10 on the rest) showed a **much stronger effect than intended**:

| k = 10, config v1 | Overview-marked papers B / I / A | Beginner ∩ Advanced |
|---|---|---:|
| graph neural networks | 9 / 1 / 0 | 1 of 10 |
| reinforcement learning in robotics | 10 / 5 / 0 | 0 of 10 |
| AI-assisted medical diagnosis | 7 / 0 / 0 | 3 of 10 |

A weak title/type cue was turning Beginner into "mostly surveys" (three overlapping recommender-system surveys for
GNN). It was also removing every overview for Advanced, against the blueprint guardrail ("still include foundations
when genuinely needed"). The M09 review's statement that experience effects were "deliberately small" was wrong at
β = 4.

**Decision:** keep experience as a scoring factor but **reduce it to a nudge**. Criteria were fixed before the
sweep:
- Overview counts are ordered B ≥ I ≥ A.
- Beginner and Advanced each keep ≥ 60% of the Intermediate papers.
- Advanced keeps ≥ 1 overview when Intermediate has ≥ 2.
- The control must change something on at least 2 of 3 topics.

Swept weights: 0, 0.01, 0.02, 0.03, 0.05, 0.075, 0.10, 0.15. Weights 0–0.02 pass everywhere; 0.03 fails GNN k = 10
(6 overviews, 5 of 10 shared). **Chosen 0.02** (config v2), the largest passing weight.

| k = 10, config v2 (0.02) | Overview-marked B / I / A | Shared with Intermediate (B / A) |
|---|---|---|
| graph neural networks | 3 / 1 / 1 | 8 / 10 |
| reinforcement learning in robotics | 6 / 5 / 2 | 7 / 7 |
| AI-assisted medical diagnosis | 0 / 0 / 0 | 10 / 10 (no effect) |

The ordering rules are the same for every experience level; experience changes only which papers are selected. The
path discloses that the order is heuristic and that difficulty is not estimated. The response is steep (0.02 → 0.03
doubles GNN Beginner overviews), so this weight stays PROVISIONAL until the M14 human review.

## D10 — what makes a defensible stage name and order? (conservative)
Use only metadata-supported names (overview cue, year range, neutral "Selected/Further papers"). Treat citations as
weak ordering evidence among selected papers only. Use chronology as the fallback. Never name Methods/Applications
stages or state prerequisites. If later review finds these names unhelpful, simplify further (ordinal "Part 1/2/3")
rather than invent categories.

## Not done
Topic-specific stage names, learner-specific ordering, ordering from abstracts (benchmark datasets carry none), any
claim that the order improves learning (M14).
