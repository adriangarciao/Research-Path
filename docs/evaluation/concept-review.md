# Concept representation review — M07 (decisions D02, D03, D08)

Date: 2026-10-17 · Policy: `ConceptPolicy` v1 with `RelevancePolicy` v3 text pipeline · Data: frozen
optimization candidates of the three 200-candidate audit snapshots (GNN 198, RL robotics 200, medical AI 167) ·
Endpoint: `GET /api/dev/snapshots/{id}/concepts` · Reviewer: Claude Code.

## Policy (conservative)
Provider topics and keywords count only at **≥ 0.5 confidence** (95% of keyword assignments qualify). Labels
merge only when their normalized form is identical (case, punctuation, plurals, known acronyms such as GNN).
Excluded, with the reason recorded in the ledger: labels naming an unrequested aspect (ethics, governance,
education, …), labels made only of query words and generic words (e.g. "graph neural networks" for a GNN query,
"machine learning", "artificial intelligence"), labels on more than 50% of candidates, one-offs (support < 2),
and anything beyond the 50 most supported concepts. Weight = log(1 + support), normalized to 1 (PROVISIONAL).

## Topic-only vs keyword-only vs combined (same frozen snapshots)

| Query | Representation | Concepts | Candidates with ≥ 1 concept | Mean per candidate | Distinct signatures | Mean pairwise Jaccard |
|---|---|---:|---:|---:|---:|---:|
| graph neural networks | topic-only | 12 | 56 / 198 (28%) | 0.28 | 13 | 0.01 |
| | keyword-only | 50 | 149 / 198 (75%) | 1.78 | 128 | 0.02 |
| | **combined** | 50 | 152 / 198 (77%) | 1.90 | 129 | 0.02 |
| reinforcement learning in robotics | topic-only | 4 | 12 / 200 (6%) | 0.06 | 5 | 0.00 |
| | keyword-only | 50 | 171 / 200 (86%) | 2.18 | 135 | 0.04 |
| | **combined** | 50 | 171 / 200 (86%) | 2.19 | 134 | 0.04 |
| AI-assisted medical diagnosis | topic-only | 5 | 26 / 167 (16%) | 0.16 | 6 | 0.01 |
| | keyword-only | 50 | 149 / 167 (89%) | 2.68 | 134 | 0.04 |
| | **combined** | 50 | 149 / 167 (89%) | 2.74 | 135 | 0.04 |

**Where broad topics fail (D02 confirmed at 200 candidates):** after removing the query-repeating primary topic
("Reinforcement Learning in Robotics" on 180/200, "Advanced Graph Neural Networks" on 115/198), topics leave
4–12 concepts covering 6–28% of candidates — almost no discrimination.
**Where keywords over-diversify:** raw confident keywords are 670–800 distinct per pool, ~75% on a single paper
(486–601 one-offs excluded); after filtering, 50 concepts with support 3–45 remain.
**Combined** keeps keyword discrimination and adds a topic fallback for papers without qualifying keywords
(GNN: +3 candidates covered).

## Recommended concepts per domain (top of the ledger)
- **GNN:** node classification (27), graph classification (22), graph representation learning (16), link
  prediction (16), graph convolutional networks (14), message passing (11), expressiveness (9), graph pooling (9).
- **RL robotics:** deep reinforcement learning (45), robot control (35), robot navigation (31), robotic manipulation
  (29), sim-to-real transfer (18), sample efficiency (17), Q-learning (16), human-robot interaction (15),
  reward shaping (10), curriculum learning (3), policy search (5), proximal policy optimization (4).
- **Medical AI:** clinical decision support (32), medical image analysis (26), explainable AI (23), large language
  models (19), convolutional neural networks (17), electronic health records (17), diagnostic imaging (16),
  diagnostic accuracy (12), computer-aided diagnosis (6), differential diagnosis (4).

## Noisy or questionable examples
- **Correctly excluded:** "graph neural networks" (187, query), "Reinforcement Learning in Robotics" topic (180,
  query), "diagnosis" (87, query), "artificial intelligence in healthcare" (48, query + generic), "AI ethics"
  (12, aspect), "AI governance", "medical education", "health economics", "liability allocation" (aspects).
- **Found and fixed during review (RelevancePolicy v3):** aspect cues had excluded technical labels —
  "multilayer perceptron", "robot perception", "linear quadratic regulator", "curriculum learning" and ~25 RL
  policy labels (policy search, policy gradient, …). RL aspect exclusions fell from 26 to 3.
- **Remaining limitations (accepted):** near-synonyms stay separate ("explainable AI", "Explainable Artificial
  Intelligence (XAI)", "interpretability", "model interpretability", "explainability"), so explainability is spread
  over several concepts; broad method labels ("deep learning") and adjacent-application labels in the medical pool
  ("robotic surgery", "personalized treatment", "telemedicine") are included; "trust in AI" and "data privacy" are
  included because they are not aspect cues. The 50-concept cap cuts at support 3.

## D03 / D08 decision (conservative)
Adopt **combined** topics + keywords with the filters above as the **provisional** coverage representation for
M08/M09. Keywords are treated as candidate subtopic signals, not validated learning objectives (D03 stays
partially open: semantic accuracy was spot-reviewed, not measured). No semantic synonym merging (D08): it would
require a curated vocabulary; revisit if the optimizer visibly over-rewards one theme split across synonyms.
