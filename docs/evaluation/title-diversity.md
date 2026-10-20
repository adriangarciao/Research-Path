# M4 / release R7 — one paper per identical title (experiment)

Status: **experiment, not adopted.** Branch `experiment/title-diversity`; nothing is merged before the owner's decision.

## Change under test
Inside greedy selection (not a post-filter), a candidate whose conservative title key equals that of an already
selected paper is no longer feasible, including landmark seeds. Publication records are untouched; same-titled works
stay distinct everywhere else. If fewer distinct titles than the budget exist, the path has fewer papers and says why.

**Title key (conservative):** Unicode NFC (canonical only, no compatibility folding), lower case (root locale),
typographic quotes/apostrophes and dashes mapped to their ASCII forms, whitespace collapsed, spaces around `:` `;`
`,` normalized, and leading/trailing punctuation and whitespace removed (e.g. a trailing period). Not folded: accents,
hyphen vs space, word order, singular/plural, abbreviations, any fuzzy or semantic similarity.

## Pre-registered criteria (fixed 2026-10-20, before any result was seen)
Compared: current default (OptimizerConfig v3) vs the same config with the title constraint, Intermediate/Balanced,
k = 5/10/15/20, on identical frozen pools: the 10 existing datasets and **5 fresh topics frozen for this experiment
before the constraint was implemented** (never used for any tuning).

| # | Criterion | Limit |
|---|---|---|
| C1 | Repeated normalized titles in a selection (with the constraint) | 0 |
| C2 | Weighted coverage loss, every topic and budget | ≤ 0.02 (R7) |
| C3 | Mean relevance evidence change | ≥ −0.01 |
| C4 | Landmarks kept at k = 10 (D16) | no topic loses more than 1; audit mean ≥ 2 and each ≥ 1 |
| C5 | D12 influence: median citation percentile | stays ≥ 0.9 wherever it was |
| C6 | Redundancy (mean pairwise concept Jaccard) | increase ≤ 0.01 |
| C7 | Budget completion | size = min(k, distinct eligible titles); any shortfall explained |
| C8 | Runtime per selection | median ≤ 1.2 × before (+ 5 ms) |
| C9 | Learning progression (reading-path stages/order) | reported; no new warnings other than the expected ones |

A failure of C2–C6 counts as a meaningful regression: then a soft title-diversity penalty is investigated (weight
chosen on the audit topics only, validated on the fresh topics) before asking for approval.

## Theory: approximation guarantee
See the section at the end (filled in with the results).
