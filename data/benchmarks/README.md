# Benchmark datasets

Frozen candidate pools exported from real candidate snapshots, so baselines and the optimizer can be compared on
**identical inputs** and reproduced offline on another machine (playbook M08). Do not edit these files: a changed
pool is a new dataset with a new name. Every benchmark run records the file's SHA-256 (computed with LF line
endings, so it is stable across Git checkouts).

| File | Query | Candidates | Snapshot | Created (UTC) | SHA-256 |
|---|---|---:|---|---|---|
| `gnn-200.json` | graph neural networks | 198 | `7357130a-6cc4-4776-8ac9-3a45e3d5a74d` | 2026-10-18 03:01:48 | `cb67e8d22bb3657ad7feb2683683d7abdf11931f68b68390ea5429adb805a710` |
| `rl-robotics-200.json` | reinforcement learning in robotics | 200 | `7e49ed44-2332-4220-bc97-f6f4ebc1e66a` | 2026-10-18 03:01:57 | `52b61eb823a178896169f1c780c983246f3fe7321422cf9d75ff17d2f2d3a306` |
| `medical-ai-200.json` | AI-assisted medical diagnosis | 167 | `4eaa4e37-f236-49d7-b31f-611d5f673e85` | 2026-10-18 03:02:07 | `ba7725f92857eaf158ea8c15b426c99e6f6123293382b8a8097515fc0104ad49` |

**How they were produced:** OpenAlex `search=` (full-text), default relevance order, 2 pages × 100, authenticated;
normalizer v2, identity policy v1 (conservative D06), relevance policy v1 (conservative D07). Each file contains
only the snapshot's *optimization candidates* (relevance-eligible and not suppressed as duplicates), with the
fields selection, metrics and ordering need. No abstracts, no credentials.

**Format:** `BenchmarkDataset` format v1 (`backend/src/main/java/dev/researchpath/benchmark/BenchmarkDataset.java`).
Unknown values (e.g. citation counts) are `null`, never zero.

**Regenerate a dataset** (requires the dev DB containing the snapshot): `.\scripts\export-benchmark.ps1 -Id <uuid> -Name <name>`.

**Attribution:** metadata from [OpenAlex](https://openalex.org) (CC0). OpenAlex work ids are kept for every record.
Concept labels and citation counts are provider data as retrieved on 2026-10-18 UTC and may differ today.
