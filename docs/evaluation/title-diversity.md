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

## Run 1 result (hard constraint) — recorded before the alternative was designed
C1, C3, C4, C5, C7, C8 pass; **C2, C6 and C9 fail**: coverage loss 0.031 / 0.027 / 0.020 on "differential privacy"
at k = 20 / 15 / 10 (removing a 1-citation 2025 book chapter titled exactly like Dwork's 2006 paper), redundancy +0.014
there at k = 10, and the quantum k = 5 path loses its only citation link. Per the rule above this is a meaningful
regression, so a soft penalty is investigated.

## Alternative pre-registered before it was run (2026-10-20): soft title-repeat penalty
Greedy gain of a candidate whose title key is already selected is reduced by λ (instead of being infeasible), so a
same-titled work is still chosen when it adds more than λ, and budgets never fall short because of titles.
Grid λ ∈ {0.005, 0.01, 0.02, 0.05, 0.1, 0.2} (for scale: one paper's quality term is at most β / k = 0.4 at k = 10).
- **Choice topics:** the 3 audit topics plus "quantum error correction" (the motivating example, already reviewed in
  M14). The audit topics alone contain a single repeat, so they cannot choose λ; this deviation is disclosed.
- **Rule:** the smallest λ that leaves no repeated title on the choice topics while C2–C6 hold on them.
- **Validation:** every other topic (including the 5 fresh M4 topics), all of C1–C9, plus the number of repeats left.
- Also reported, as a variant only: the hard constraint with hyphen and space treated alike in the key.

## Theory: approximation guarantee
See the section at the end (filled in with the results).
