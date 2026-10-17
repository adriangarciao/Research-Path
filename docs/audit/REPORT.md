# ResearchPath — OpenAlex Metadata Audit

**Run timestamp:** 2026-10-16 21:41:32 UTC
**Source:** Live OpenAlex API
**Sample target:** 10 records per topic; top-ranked search results, not random samples
**Confidence cutoffs:** topics ≥ 0.15; keywords ≥ 0.25

## Quick comparison

| Research query | Works | Abstracts | Topics | Keywords | Primary topic concentration | Unique keywords |
|---|---:|---:|---:|---:|---:|---:|
| graph neural networks | 10 | 90.0% | 100.0% | 100.0% | 80.0% | 55 |
| reinforcement learning in robotics | 10 | 60.0% | 100.0% | 100.0% | 100.0% | 53 |
| AI-assisted medical diagnosis | 10 | 80.0% | 100.0% | 100.0% | 60.0% | 103 |

## What the metrics mean

- **Abstracts/topics/keywords:** Percentage of returned records with the metadata needed to build features.
- **Primary topic concentration:** Percentage sharing the most common first topic; a warning for coarse classification, not a quality judgment.
- **Jaccard overlap:** Average shared-tag fraction among paper pairs *where both have that tag type*. Higher means more similar metadata assignments. Different tag vocabularies mean topic-vs-keyword values are suggestive, not directly calibrated.
- **Same-topic/different-keyword pairs:** Among pairs with both tag types, how often identical topic sets conceal different keyword sets.
- **Internal citation edges:** Citation links between works in this tiny returned sample. Low values do not imply weak citation data globally.

## Findings by topic

### graph neural networks

- Works retrieved: **10** (provider reports 106991 total matches).
- Complete titles **100.0%**; abstracts **90.0%**; DOI **90.0%**; source links **100.0%**.
- At least one topic **100.0%**; at least one keyword **100.0%**; at least one indexed reference **100.0%**.
- Distinct topic labels **3**; distinct keywords **55**; average tags/paper **1.0 topics / 7.8 keywords**.
- Most common primary topic: **Advanced Graph Neural Networks** (80.0% of sample).
- Mean pairwise Jaccard: topics **0.622**, keywords **0.108** (None means insufficient comparable pairs).
- Same topic tags but different keyword tags: **27 / 45** eligible paper pairs; internal citation edges: **17**.

**Most frequent topic labels:** Advanced Graph Neural Networks (8), Recommender Systems and Techniques (1), Functional Brain Connectivity Studies (1)

**Most frequent keywords:** graph neural networks (10), graph convolutional networks (4), non-Euclidean data (3), graph-structured data (2), graph data (2), recurrent graph neural networks (2), graph autoencoder (2), spatio-temporal graph neural networks (2), benchmark dataset (2), neighborhood aggregation (2), graph representation learning (2), disease classification (2)

**Automated diagnostic flags:**
- At least 70% share the same primary topic: topic-only coverage may be too coarse.
- Keyword sets are noticeably more differentiated than topic sets; test keyword-level coverage.

**Sample records (open and verify their relevance):**

| Publication | Year | Primary topic | Leading keywords |
|---|---:|---|---|
| [Graph neural networks](https://openalex.org/W4392553889) | 2024 | Advanced Graph Neural Networks | graph neural networks, graph-structured data, deep learning |
| [The Graph Neural Network Model](https://openalex.org/W2116341502) | 2008 | Advanced Graph Neural Networks | graph neural networks, graph-structured data, node embeddings |
| [Benchmarking Graph Neural Networks](https://openalex.org/W3007332492) | 2020 | Advanced Graph Neural Networks | graph neural networks, benchmarking framework, molecular property prediction |
| [A Comprehensive Survey on Graph Neural Networks](https://openalex.org/W2907492528) | 2020 | Advanced Graph Neural Networks | graph neural networks, graph data, non-Euclidean data |
| [Graph Neural Networks for Social Recommendation](https://openalex.org/W2914721378) | 2019 | Recommender Systems and Techniques | social recommendation, graph neural networks, user-item interaction graph |
| [A Comprehensive Survey on Graph Neural Networks](https://openalex.org/W4210257598) | 2020 | Advanced Graph Neural Networks | graph neural networks, graph data, non-Euclidean data |
| [How Powerful are Graph Neural Networks?](https://openalex.org/W2894175828) | 2018 | Advanced Graph Neural Networks | graph neural networks, expressiveness, neighborhood aggregation |
| [Graph Neural Networks in Network Neuroscience](https://openalex.org/W3169752883) | 2022 | Functional Brain Connectivity Studies | graph neural networks, brain graphs, brain connectivity |
| [Towards Deeper Graph Neural Networks](https://openalex.org/W3042770487) | 2020 | Advanced Graph Neural Networks | graph neural networks, over-smoothing, graph convolution |
| [Graph neural networks: A review of methods and applications](https://openalex.org/W3152893301) | 2020 | Advanced Graph Neural Networks | graph neural networks, graph convolutional networks, graph attention networks |

### reinforcement learning in robotics

- Works retrieved: **10** (provider reports 26622 total matches).
- Complete titles **100.0%**; abstracts **60.0%**; DOI **90.0%**; source links **100.0%**.
- At least one topic **100.0%**; at least one keyword **100.0%**; at least one indexed reference **90.0%**.
- Distinct topic labels **1**; distinct keywords **53**; average tags/paper **1.0 topics / 6.7 keywords**.
- Most common primary topic: **Reinforcement Learning in Robotics** (100.0% of sample).
- Mean pairwise Jaccard: topics **1.0**, keywords **0.053** (None means insufficient comparable pairs).
- Same topic tags but different keyword tags: **45 / 45** eligible paper pairs; internal citation edges: **5**.

**Most frequent topic labels:** Reinforcement Learning in Robotics (10)

**Most frequent keywords:** reinforcement learning (5), policy search (3), deep reinforcement learning (3), robot behavior generation (2), state representation (2), robotics (2), autonomous robots (2), robot control (2), robotic manipulation (2), robot soccer (1), RoboCup (1), offline reinforcement learning (1)

**Automated diagnostic flags:**
- Abstract coverage below 70%: abstract-dependent difficulty/relevance scoring may be unreliable.
- At least 70% share the same primary topic: topic-only coverage may be too coarse.
- Keyword sets are noticeably more differentiated than topic sets; test keyword-level coverage.

**Sample records (open and verify their relevance):**

| Publication | Year | Primary topic | Leading keywords |
|---|---:|---|---|
| [Reinforcement learning in robotics: A survey](https://openalex.org/W1977655452) | 2013 | Reinforcement Learning in Robotics | reinforcement learning, robot behavior generation, policy search |
| [Reinforcement learning for robot soccer](https://openalex.org/W2127412976) | 2009 | Reinforcement Learning in Robotics | reinforcement learning, robot soccer, RoboCup |
| [Reinforcement Learning in Robotics: A Survey](https://openalex.org/W2965916140) | 2013 | Reinforcement Learning in Robotics | reinforcement learning, robot behavior generation, policy search |
| [Reinforcement learning in robotic applications: a comprehensive survey](https://openalex.org/W3152878473) | 2021 | Reinforcement Learning in Robotics | reinforcement learning, robotics, autonomous robots |
| [Reinforcement learning for robots using neural networks](https://openalex.org/W1595483645) | 1992 | Reinforcement Learning in Robotics | reinforcement learning, robot learning, artificial neural networks |
| [Residual Reinforcement Learning for Robot Control](https://openalex.org/W2904746163) | 2019 | Reinforcement Learning in Robotics | residual reinforcement learning, hybrid control, feedback control |
| [Reinforcement Learning in Robotics: A Survey](https://openalex.org/W4211089519) | 2012 | Reinforcement Learning in Robotics | autonomous robots, sequential decision problems, behavior generation |
| [Sim-to-Real Transfer in Deep Reinforcement Learning for Robotics: a Survey](https://openalex.org/W3088310808) | 2020 | Reinforcement Learning in Robotics | sim-to-real transfer, deep reinforcement learning, robotics |
| [Review of Deep Reinforcement Learning for Robot Manipulation](https://openalex.org/W2930863966) | 2019 | Reinforcement Learning in Robotics | deep reinforcement learning, robotic manipulation, dexterous manipulation |
| [Composable Deep Reinforcement Learning for Robotic Manipulation](https://openalex.org/W2792404612) | 2018 | Reinforcement Learning in Robotics | soft Q-learning, robotic manipulation, energy-based models |

### AI-assisted medical diagnosis

- Works retrieved: **10** (provider reports 4472 total matches).
- Complete titles **100.0%**; abstracts **80.0%**; DOI **90.0%**; source links **100.0%**.
- At least one topic **100.0%**; at least one keyword **100.0%**; at least one indexed reference **60.0%**.
- Distinct topic labels **5**; distinct keywords **103**; average tags/paper **1.3 topics / 11.3 keywords**.
- Most common primary topic: **Artificial Intelligence in Healthcare and Education** (60.0% of sample).
- Mean pairwise Jaccard: topics **0.37**, keywords **0.014** (None means insufficient comparable pairs).
- Same topic tags but different keyword tags: **10 / 45** eligible paper pairs; internal citation edges: **0**.

**Most frequent topic labels:** Artificial Intelligence in Healthcare and Education (7), Explainable Artificial Intelligence (XAI) (3), Mobile Health and mHealth Applications (1), Technology Adoption and User Behaviour (1), Medical Malpractice and Liability Issues (1)

**Most frequent keywords:** large language models (3), artificial intelligence in healthcare (2), AI-assisted diagnosis (2), clinical decision support (2), clinical validation (2), decision support (2), explainable AI (2), lime (2), privacy (2), diagnosis (1), health policy (1), economic value (1)

**Automated diagnostic flags:**
- Keyword sets are noticeably more differentiated than topic sets; test keyword-level coverage.

**Sample records (open and verify their relevance):**

| Publication | Year | Primary topic | Leading keywords |
|---|---:|---|---|
| [The Role for Policy in AI-Assisted Medical Diagnosis](https://openalex.org/W4394914187) | 2024 | Artificial Intelligence in Healthcare and Education | artificial intelligence in healthcare, large language models, diagnosis |
| [Research on the Economic Value and Risk Governance of AI-Assisted Medical Diagnosis](https://openalex.org/W7212174198) | 2026 | Artificial Intelligence in Healthcare and Education | economic value, risk governance, health economics |
| [Human–machine interaction in AI-assisted medical diagnosis: Challenges and future directions](https://openalex.org/W7155557682) | 2026 | Artificial Intelligence in Healthcare and Education | human-machine interaction, explainable AI, trust in AI |
| [Knowledge Integration and Decision Support of Large Language Models in AI-Assisted Medical Diagnosis](https://openalex.org/W4416725044) | 2025 | Artificial Intelligence in Healthcare and Education | large language models, knowledge integration, decision support |
| [AI Assisted Medical Diagnosis of Lung Diseases Using Accurate Symptoms Visualization](https://openalex.org/W4312390217) | 2022 | Mobile Health and mHealth Applications | lung disease, mobile health apps, symptom monitoring |
| [Enhancing Public Trust and Acceptance of Ai-Assisted Medical Diagnosis Systems Through Explainabilit](https://openalex.org/W4406739702) | 2025 | Explainable Artificial Intelligence (XAI) | public trust, technology acceptance, explainability |
| [AI-Assisted Medical Diagnosis: A Hybrid XAI Framework for Interpretable Transformer-Based Text Class](https://openalex.org/W7113096017) | 2025 | Explainable Artificial Intelligence (XAI) | explainable AI, lime, prompt engineering |
| [Risk Allocation and Incentive Mechanisms in AI-Assisted Medical Diagnosis: A Cross-Disciplinary Anal](https://openalex.org/W7154488024) | 2026 | Medical Malpractice and Liability Issues | liability allocation, risk allocation, incentive mechanisms |
| [AI-ASSISTED MEDICAL IMAGE DIAGNOSIS MOBILE APPLICATION](https://openalex.org/W7216238055) | 2026 | Artificial Intelligence in Healthcare and Education | medical image analysis, mobile application, flutter |
| [ANALYZING THE IMPACT OF AI-ASSISTED DIAGNOSIS ON MEDICAL STUDENTS' UNDERSTANDING OF NEUROLOGICAL DIS](https://openalex.org/W4383817571) | 2023 | Artificial Intelligence in Healthcare and Education | AI-assisted diagnosis, medical students, neurological disorders |

## Decision guidance for ResearchPath

1. **Keep broad topics as a high-level layer**, not the only coverage representation, if the same primary topic dominates a query.
2. **Use finer keywords as candidate subtopic signals** if they are present, diverse, and relevant. Do not count unique tags as proof of actual learning coverage.
3. **Use graceful fallbacks for missing abstracts** rather than generating difficulty labels without evidence.
4. **Verify roughly 3–5 papers per topic manually** for relevance, keyword accuracy, and whether the sequence could be educational. Automated metadata checks cannot establish semantic usefulness.
5. **Repeat at larger pool sizes and more varied sampling** before finalizing scoring weights. This audit targets top-ranked records only and may overestimate relevance.

## Files generated

- `raw/*.json` — untouched provider response snapshots for reproducibility.
- `papers.csv` — one row per retrieved publication with normalized metadata fields.
- `summary.json` — metrics and warning flags for automated processing.
- `REPORT.md` — this human-readable report.

## Caveats

OpenAlex topics and keywords are model-assigned and may contain errors. Citation counts depend on age and coverage; citation links do not imply learning prerequisites. Abstract availability is not readability. Topic diversity is a metadata proxy, not evidence that a person learned more.
