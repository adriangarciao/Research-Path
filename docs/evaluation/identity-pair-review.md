# Identity pair review — M05 (decision D06)

Date: 2026-10-17 · Policy: `IdentityPolicy` v1 (conservative default, owner-approved) ·
Fixture: `backend/src/test/resources/identity/reviewed-pairs.json` · Test: `ReviewedIdentityPairsTest`

**Reviewer:** Claude Code, from authors, venues, years, DOIs and landing pages stored in the dev DB.
**Owner spot-check: requested** (playbook M05 gate). Labels are judgments, not ground truth.

## Policy (D06 conservative default)
Keep ambiguous versions as separate records; suppress only **obvious** duplicates from a snapshot's
candidate set. Nothing is merged or deleted; every decision is stored with its rule and evidence.
Suppression happens only for `SAME_WORK`: (a) same DOI **and** same normalized title, or (b) same
normalized title + same year + shared author (OpenAlex id or surname + first initial) + at most one DOI.
Everything else — conflicting DOIs, year gaps, missing authors/years, DOI/title conflicts — keeps both works.

## Population
129 pairs flagged in the dev DB (470 works, 7 snapshots): **0 same-DOI pairs**, 129 same-normalized-title
pairs, ~100 of them from the generic title "Protein structure prediction" (1990–2021, no shared authors).
17 representative pairs were reviewed, plus one pair found by the live run (18 total).

## Reviewed pairs

| Pair | Works | Reviewer label | Policy decision | Outcome |
|---:|---|---|---|---|
| 1 | IEEE TNNLS 2020 survey vs UTS repository copy | SAME_WORK | SAME_WORK | ✅ suppressed (DOI record kept) |
| 2 | GNNExplainer arXiv preprint vs PMC copy (2019) | SAME_WORK | SAME_WORK | ✅ suppressed |
| 29 | Contemporary Physics 2019 vs White Rose accepted manuscript | SAME_WORK | SAME_WORK | ✅ suppressed (name formats differ) |
| 160 | Steane 1996 Proc. R. Soc. A vs CiteSeerX copy (found live, then reviewed) | SAME_WORK | SAME_WORK | ✅ suppressed (titles differ by a hyphen) |
| 13 | SPIE proceedings 2002 vs dblp 2001 record, same paper | SAME_WORK | PROBABLE_VERSION | ⚠️ missed duplicate (year off by one) |
| 28 | Gottesman thesis vs arXiv copy (1997) | SAME_WORK | PROBABLE_VERSION | ⚠️ missed duplicate (conflicting DOIs) |
| 16 | Journal article vs Zenodo deposit credited to the journal | SAME_WORK | DISTINCT | ⚠️ missed duplicate (bad author metadata) |
| 6 | IJRR 2013 survey vs 2013 book-chapter republication | DISTINCT_VERSION | PROBABLE_VERSION | ✅ kept, flagged |
| 8 | Two book-chapter versions (2012/2013) of that survey | DISTINCT_VERSION | PROBABLE_VERSION | ✅ kept, flagged |
| 15 | Annual Review 2024 vs AAAI 2025, same title/authors | DISTINCT_VERSION | PROBABLE_VERSION | ✅ kept, flagged |
| 135 | Same authors' chapters 2017 vs 2019, different DOIs | UNCERTAIN | PROBABLE_VERSION | ✅ kept, flagged |
| 42 | 2014 book vs 2014 chapter, no author data | UNCERTAIN | UNRESOLVED | ✅ kept |
| 3 | "Graph neural networks" review 2024 vs chapter 2022 | DIFFERENT_WORK | DISTINCT | ✅ kept |
| 69 | "Progress in protein structure prediction" 1997 vs 2001 | DIFFERENT_WORK | DISTINCT | ✅ kept |
| 115 | 2017 article vs 2012 entry, one shared author | DIFFERENT_WORK | DISTINCT | ✅ kept |
| 9 | Apress 2018 vs Springer 2026 chapters, different authors | DIFFERENT_WORK | DISTINCT | ✅ kept |
| 12 | 2002 SPIE paper vs 2026 Zenodo preprint, other author | DIFFERENT_WORK | DISTINCT | ✅ kept |
| 36 | 2001 review vs 2014 book (one side no authors) | DIFFERENT_WORK | DISTINCT | ✅ kept |

**Results on 18 reviewed pairs:** false merges **0 / 11** non-duplicate pairs · duplicates suppressed
**4 / 7** · missed duplicates **3 / 7** (all in the conservative direction; documented above).

## Live run (V4, 2026-10-17, fresh 100-candidate snapshots)

| Topic | SAME_WORK pairs | PROBABLE_VERSION | DISTINCT | UNRESOLVED | Suppressed members |
|---|---:|---:|---:|---:|---:|
| graph neural networks | 2 | 0 | 3 | 0 | 2 (pairs 1, 2) |
| quantum error correction | 2 | 7 | 80 | 0 | 2 (pairs 29, 160) |
| reinforcement learning in robotics | 0 | 5 | 5 | 0 | 0 |

Every live suppression was inspected; all four are reviewed SAME_WORK pairs and the canonical member is
the record with a DOI. The seven snapshots created before V4 report no identity block (not evaluated).

## Missed duplicates outside detection (near-identical titles)
One-off scan of all 470 works for similar-but-unequal titles sharing an author surname (token Jaccard ≥ 0.75
or prefix): 4 pairs.
- **Undetected versions (2):** "Shuffled Model of Federated Learning…" preprint 2020 vs article 2021 (title
  words reordered); "Importance of Patient History in AI-Assisted Medical Diagnosis" preprint 2023 vs article
  2024. Both would be PROBABLE_VERSION (kept) even if detected, so no suppression is lost.
- **Would be false merges under fuzzy matching (2):** CASP "Round VII" (2007) vs "Round VIII" (2009), same
  authors, different papers; membrane-protein papers 1992 vs 2007. This supports exact-title detection.

## Known limitations
- Versions (preprint/journal, republished chapters) are **not** suppressed. Both may be eligible for one
  path; avoiding that is a selection concern (M09: treat PROBABLE_VERSION as a soft constraint — to decide).
- Author metadata errors (pair 16) and year mismatches (pair 13) defeat the rules by design.
- Sample is the top-ranked results of 7 queries; no same-DOI pair has been observed yet (covered by unit tests).
- Data-quality note: one stored DOI ends in "." (`10.7907/rzr7-dt72.`); DOI normalization left unchanged.
