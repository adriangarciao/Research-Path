# Evidence: first 100-candidate snapshots (2026-10-17)

**What was run:** the new ingestion pipeline (`POST /api/dev/snapshots`, normalizer v1) against live
OpenAlex, 100 candidates per topic, for the same three topics as the 30-paper audit.
Retrieval: OpenAlex `search=` (full-text), default relevance order, pages of 100, authenticated.
Took 3–6 s per snapshot. Each search page cost USD 0.001 according to OpenAlex's `meta.cost_usd`.

**Status: OBSERVED, limited.** These are still the *top 100 search-ranked* works per query (not a random or
deeper sample), with no manual relevance, duplicate, or keyword-quality judgments yet. Counts below are
descriptive. They were cross-checked by recomputing every number directly from the stored raw provider
JSON (`snapshot_membership.source_record`); both paths agreed exactly.

| Query | Provider matches | Stored | Abstract | ≥1 topic | ≥1 keyword | Has references | DOI | Distinct topics | Distinct keywords | Most common primary topic | Internal citation edges | Possible duplicate pairs |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|---:|
| graph neural networks | 964,424 | 100 | 89 | 100 | 100 | 91 | 97 | 55 | 392 | Advanced Graph Neural Networks (57) | 304 | 5 |
| reinforcement learning in robotics | 191,123 | 100 | 77 | 100 | 100 | 68 | 86 | 36 | 397 | Reinforcement Learning in Robotics (92) | 70 | 10 |
| AI-assisted medical diagnosis | 250,306 | 100 | 86 | 100 | 100 | 58 | 95 | 61 | 603 | AI in Healthcare and Education (54) | 5 | 1 |

Topic and keyword counts use **no confidence threshold**. The audit used topic ≥0.15 / keyword ≥0.25, so the
counts are not directly comparable. Some secondary topic scores are as low as 0.0003.

## What this adds to the decision register (blueprint §13)

- **D02 (topics too coarse):** The concentration persists at 100 candidates: one primary topic covers 92% of
  robotics and 57% of GNN works. Fine keywords remain necessary.
- **D04 (abstract availability):** 77–89% at 100 candidates, versus 60–90% in the 10-work audit.
  The missing-abstract fallback is still required.
- **D05 (citation graph):** Internal edges are 304 / 70 / **5**. Medical-AI is nearly disconnected even at
  100 works, so the ordering must work without citation edges. 9–42% of works have no outgoing references at all.
- **D06 (versions):** Of the 16 flagged pairs, several reproduce the audit's cases. "A Comprehensive Survey on
  GNNs" appears with and without a DOI, and the robotics survey appears as an article plus two book chapters.
  Others show why title-only merging is wrong: "Graph neural networks" is a 2024 review, a 2022 book chapter
  and a 2023 conference paper, and "RL for robot control" appears as a 2002 paper and a 2026 preprint.
  This supports evidence-only recording for now. The merge policy is still OPEN.
- **D11 (pool size / cost):** 100 candidates cost about 1 request and a few seconds. Cost is not the
  constraint at this size. Relevance at depth is still unmeasured.
- **D03 / D07 / D08:** Unchanged (OPEN). 392–603 distinct keywords per 100 works makes generic-term and
  relevance filtering more important, not less.

## Not yet done (next steps)

- Retrieve beyond the first-ranked 100 (pages 2+), and compare `search=` with `title_and_abstract.search`.
- Manually label a varied subset per topic for relevance, duplicate/version relation, and keyword accuracy
  (Appendix C, step 3).
