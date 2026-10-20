# M4 / release R7 — one paper per identical title (experiment)

Status: **experiment, not adopted — awaiting the owner's decision.** Branch `experiment/title-diversity` (local, not
merged, not pushed). Full tables: [before/after](benchmarks/title-diversity.md), [variants](benchmarks/title-diversity-variants.md).

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
- **Today (documented):** F(S) = weighted probabilistic coverage + (β / k) · Σ quality is monotone submodular, so plain
  greedy under the budget alone is within (1 − 1/e) ≈ 0.632 of the best set of k papers. The D16 landmark seeds are
  placed before greedy and are already outside that statement (documented as an empirical property test only).
- **With one paper per title:** the feasible sets are those with ≤ k papers and ≤ 1 per title key: a truncated
  partition matroid. For a monotone submodular objective over one matroid, greedy guarantees only **1/2** of the
  constrained optimum (Fisher, Nemhauser & Wolsey 1978); (1 − 1/e) needs the continuous-greedy / pipage machinery,
  which we would not adopt for this. The objective itself is unchanged, and the optimum it is compared with is the
  best set **without repeated titles**, which can be lower than the unconstrained optimum.
- **When no title repeats in the pool** (most of the pool for most topics) the matroid equals the cardinality
  constraint and the (1 − 1/e) statement is unchanged.
- **Empirical (tiny pools, exhaustive search, no seeds):** worst ratio 0.931 over 80 random pools with 2–6 shared
  titles, optimal in 65, never below 1 − 1/e (`TitleConstraintTest`). The previous cardinality-only property tests
  still pass unchanged.
- **Soft penalty:** subtracting λ per repeated title makes the objective non-monotone (still submodular), so neither
  guarantee applies; it would have to be documented as a heuristic.

## Results (15 frozen pools × k = 5/10/15/20, 60 selections)
**Hard constraint (as specified):**
- **Repeats:** 19 repeated titles before → 0 after. Selections are identical on 9 of 15 topics; 6 change.
- **Quantum error correction:** "Quantum Error Correction" appears 2 / 3 / 4 / 3 times at k = 5 / 10 / 15 / 20 before
  (counting by title key), exactly once after. Coverage −0.017 / −0.013 / −0.015 / +0.003. At k = 5 the path loses
  its only citation link (order falls back to metadata: `NO_CITATION_LINKS`). "Quantum error-correction" (hyphen)
  stays: the conservative key keeps hyphen and space distinct.
- **Differential privacy (fresh):** coverage −0.020 / −0.027 / −0.031 at k = 10 / 15 / 20, redundancy +0.014 at
  k = 10, landmarks 3 → 2 at k = 15/20 (unchanged at k = 10), median percentile 0.986 → 0.994 at k = 10. Cause:
  excluding a 2025 book chapter titled "Differential Privacy" (1 citation) next to Dwork's 2006 paper (5,861).
- **Elsewhere:** compressed sensing +0.023 (k = 10) / −0.015 (k = 15, 20) with one more landmark; GNN k = 20
  +0.017; causal economics k = 20 unchanged coverage.
- **Unchanged everywhere:** relevance (Δ = 0.000), budget completion (no shortfall; every pool has ≥ 149 distinct
  titles), landmarks at k = 10 (max loss 0; audit mean 2.33), median runtime 20.8 → 20.8 ms.
- **Pre-registered verdict:** C1, C3, C4, C5, C7, C8 pass; **C2 (0.031 > 0.02), C6 (0.014 > 0.01) and C9 fail.**

**Soft penalty (pre-registered alternative):** no λ avoids the trade-off. λ ≤ 0.01 stays inside every metric
limit only by keeping 10 of 19 repeats (including on quantum); λ = 0.02 keeps 5 and still fails C6; λ ≥ 0.05 (the
value the pre-registered rule picks) behaves exactly like the hard constraint. The cost comes from removing those
particular same-titled works, not from how they are removed.

**Hyphen-insensitive key (variant):** also removes "Quantum error-correction", but raises the quantum coverage loss
to 0.046; outside the requested conservative normalization.

**Learning progression:** 10 of 60 stage structures change (year ranges shift; differential privacy k = 20 gets
5 overview-type works in the first stage instead of 2); citation links change by −7 to +1; the only new warning is
the quantum k = 5 `NO_CITATION_LINKS` above.

**Verification on the branch:** `verify.ps1` PASS (352 backend tests) after updating three deliberate pins
(GNN selection and reading-path fingerprints — one paper changes at k = 20 — and the stored optimizer-config version
3 → 4); browser e2e 7 passed (5 mobile skips, as before); live: a new "quantum error correction" path (k = 10)
contains "Quantum Error Correction" once.

## Note on the metrics
Coverage is the optimizer's own objective, so a loss partly measures "different from the unconstrained greedy
choice"; it is not evidence that the path teaches less. In the differential-privacy case the excluded work is a
barely cited chapter that repeats the title of the field's founding paper.
