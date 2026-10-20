# One paper per title (M4 / R7) — variants

Each variant against the unchanged default on the same frozen pools. Choice topics: AI-assisted medical diagnosis, graph neural networks, quantum error correction, reinforcement learning in robotics; validation: all other topics. Repeats = selected works sharing a conservative title key. Soft-penalty choice rule (pre-registered): the smallest λ with no repeats and C2–C6 on the choice topics.

**Soft choice:** soft λ=0.05

| Variant | Topics | Repeats left (before) | Max coverage loss | Worst relevance Δ | Max redundancy rise | Max landmark loss k=10 | Changed papers | Failed criteria |
|---|---|---|---|---|---|---|---|---|
| hard constraint | choice | 0 (9) | 0.017 | 0.000 | 0.001 | 0 | 12 | C9 |
| hard constraint | validation | 0 (10) | 0.031 | 0.000 | 0.014 | 0 | 29 | C2, C6 |
| hard constraint, hyphen = space | choice | 0 (9) | 0.046 | 0.000 | 0.001 | 0 | 20 | C2 |
| hard constraint, hyphen = space | validation | 0 (10) | 0.031 | 0.000 | 0.014 | 0 | 29 | C2, C6 |
| soft λ=0.005 | choice | 5 (9) | 0.000 | 0.000 | 0.001 | 0 | 5 | C1 |
| soft λ=0.005 | validation | 5 (10) | 0.015 | 0.000 | 0.009 | 0 | 13 | C1 |
| soft λ=0.01 | choice | 5 (9) | 0.000 | 0.000 | 0.001 | 0 | 5 | C1 |
| soft λ=0.01 | validation | 5 (10) | 0.015 | 0.000 | 0.009 | 0 | 13 | C1 |
| soft λ=0.02 | choice | 3 (9) | 0.017 | 0.000 | 0.001 | 0 | 7 | C1, C9 |
| soft λ=0.02 | validation | 2 (10) | 0.020 | 0.000 | 0.014 | 0 | 17 | C1, C6 |
| soft λ=0.05 | choice | 0 (9) | 0.017 | 0.000 | 0.001 | 0 | 12 | C9 |
| soft λ=0.05 | validation | 0 (10) | 0.031 | 0.000 | 0.014 | 0 | 29 | C2, C6 |
| soft λ=0.1 | choice | 0 (9) | 0.017 | 0.000 | 0.001 | 0 | 12 | C9 |
| soft λ=0.1 | validation | 0 (10) | 0.031 | 0.000 | 0.014 | 0 | 29 | C2, C6 |
| soft λ=0.2 | choice | 0 (9) | 0.017 | 0.000 | 0.001 | 0 | 12 | C9 |
| soft λ=0.2 | validation | 0 (10) | 0.031 | 0.000 | 0.014 | 0 | 29 | C2, C6 |
