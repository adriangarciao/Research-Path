# Relevance review — M06 (decisions D07, D11)

Date: 2026-10-17 · Policy: `RelevancePolicy` v1 (conservative; owner pre-approved conservative picks) ·
Fixture: `backend/src/test/resources/relevance/labeled-candidates.json` · Test: `RelevanceLabeledSetTest`

**Reviewer:** Claude Code, from title, keywords and abstract. Labels are judgments, not ground truth.
**Important:** this labeled set was also the development set for the aspect lexicon and exceptions, so the
numbers below are optimistic for unseen queries. They show the gate behaves safely on these pools, not that
it generalizes.

## Policy (D07 conservative)
Only **ELIGIBLE** candidates may enter optimization. A candidate is ELIGIBLE when ≥ 75% of the query's content
terms appear in its **title or confident keywords/topics** (audit thresholds: topic ≥ 0.15, keyword ≥ 0.25;
unknown scores and labels naming an unrequested aspect do not count) and its title is not mainly about an
unrequested aspect. The abstract is **never required**. Everything else is excluded but kept with evidence:
TANGENTIAL (unrequested aspect: policy, governance, regulation, legal, liability, ethics, economics,
staffing, education, students, curriculum, perceptions, attitudes, intention, acceptance, adoption, distrust,
information security), BORDERLINE (partial evidence), INELIGIBLE (no evidence).
Technical senses are exempt (RL "policy", teacher–student "student"); "trust", "security", "perspective" and
"law" are deliberately **not** cues because the pools contain technical work using them.

## Labeled set
118 real candidates from the 200-candidate snapshots of the three audit topics: every 10th rank (plus ranks 1
and 5), every title containing a possible aspect word, and 15 cross-topic records (real papers from other pools
judged against the wrong query). Labels: 76 RELEVANT, 23 TANGENTIAL, 15 UNRELATED, 4 UNCERTAIN.

| Query | Label | ELIGIBLE | TANGENTIAL | BORDERLINE | INELIGIBLE |
|---|---|---:|---:|---:|---:|
| graph neural networks | RELEVANT (24) | 24 | 0 | 0 | 0 |
| | UNRELATED (5) | 0 | 0 | 0 | 5 |
| reinforcement learning in robotics | RELEVANT (28) | 28 | 0 | 0 | 0 |
| | UNRELATED (5) | 0 | 0 | 0 | 5 |
| | UNCERTAIN (1) | 1 | 0 | 0 | 0 |
| AI-assisted medical diagnosis | RELEVANT (24) | 23 | 0 | 1 | 0 |
| | TANGENTIAL (23) | 6 | 15 | 2 | 0 |
| | UNRELATED (5) | 0 | 0 | 0 | 5 |
| | UNCERTAIN (3) | 0 | 3 | 0 | 0 |

**Summary:** relevant recall **75 / 76**; unrelated admitted **0 / 15**; tangential excluded **17 / 23**;
all 7 technical "policy" robotics titles and both "teaching" robotics titles stay ELIGIBLE; no relevant paper
was excluded for lacking an abstract (32 relevant items have no abstract).

### False positives (tangential admitted — 6, all medical)
- *A Comprehensive Review on AI Techniques for Healthcare*; *AI and ML Fundamentals* — broad healthcare
  overviews whose confident keywords include "diagnosis" and "medical …". Arguably borderline labels.
- *Practice and Evaluation of AI-Assisted Pathological Diagnosis in Clinical Teaching* — education ("teaching"
  is not a cue because robotics uses it technically).
- *User Trust Modeling …*; *Explainable Fuzzy Cognitive Modelling of Human Trust …* — trust studies ("trust" is
  not a cue because XAI/trustworthiness papers are relevant).
- *Clinical evaluation of an NLP system for assisting structured diagnosis recording (MiADE)* — documentation
  tool whose title contains every query term.

### False negative (relevant excluded — 1)
- *Artificial Intelligence in Healthcare: Diagnosis and Treatment* — title lacks "medical"/"assisted" →
  BORDERLINE. "healthcare" is not treated as a synonym of "medical" (doing so would admit more broad overviews).

### Uncertain items
"Adoption of machine learning for medical diagnosis" (×2) and "Integrating Explainable AI in Medical Devices:
Technical, Clinical and Regulatory Insights" → TANGENTIAL; "Teaching approach for deep RL of robotic
strategies" → ELIGIBLE.

## Why lexical evidence, and its limits
The gate is explainable (each decision lists matched/missing terms and cues) and deterministic. It cannot read
intent: a paper whose title contains every query word but is about documentation or trust will pass. The
medical-AI topic is where this matters; GNN and RL-robotics pools were on-topic throughout. Improving this
would need semantic evidence (out of scope for v1) or human review of tangential examples.
