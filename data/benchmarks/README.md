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
| `protein-structure-200.json` | protein structure prediction | 200 | `dec42238-28a6-444b-a7a1-b2fb7a35355f` | 2026-10-18 20:25:16 | `695bdc70c692b1528b37d5f0df0683073cbf2f07d3df29b2f33f602c7147bfe9` |
| `causal-economics-200.json` | causal inference in economics | 62 | `dfb0b49d-5777-4540-a3fb-c81307e328de` | 2026-10-18 20:25:40 | `e5b561839ff644061f76008952de537f1df2e7f4ef7dc5105406d2d74c388589` |
| `quantum-ec-200.json` | quantum error correction | 198 | `c748ec22-3500-4474-82a6-78cdbfa635ee` | 2026-10-18 20:25:58 | `9aba207e11752c162f7c3320cb37ed967ffa1cc28e78f4343fb93e4241a9be31` |
| `climate-agriculture-200.json` | climate change adaptation in agriculture | 183 | `58eeb968-259c-4b2d-837e-5ab0cff0b83d` | 2026-10-18 20:26:17 | `0ed92ca58a8e57bdd5b3ddbcfeb11d2defb075ed0edbd3bd3aa742135d2790d9` |
| `nmt-200.json` | neural machine translation | 199 | `aaf7b1c5-6e76-4907-aec9-aaab75811a23` | 2026-10-20 03:20:39 | `f61f58f45bb17d3d9e5d638a69fb0d563f0e91a82fe157a49959ee887b4104ee` |
| `crispr-200.json` | CRISPR gene editing | 189 | `70b5739b-d29f-4471-bc3a-52e6e6d4e17e` | 2026-10-20 03:20:49 | `56e87fc46751b733c57f4f635ff1ad4967d73ca15d3eff987799bb945e9ffe0d` |
| `urban-heat-200.json` | urban heat island mitigation | 194 | `cf3e4292-3b67-4528-941c-0dfc84637f28` | 2026-10-20 03:20:57 | `b195f4f9a444b9ba5066f8d81844440676f70cb4cdae9cff9406737711740228` |
| `compressed-sensing-200.json` | compressed sensing | 197 | `cb167b6b-b83d-409e-ae91-8fc0aa85819d` | 2026-10-20 22:50:16 | `b8817730e1da46b95a09a8cb05fed09900906e581d10a6db9821c850b28373ad` |
| `differential-privacy-200.json` | differential privacy | 197 | `ec02c56b-b2d7-4f0c-9999-fb520bbb98eb` | 2026-10-20 22:50:25 | `71f2e6239bb5fdf0f552e5cce5d54f587c4cdc8b7174e9a565a990fc52ec8df7` |
| `topological-insulators-200.json` | topological insulators | 196 | `bc2c4d68-ae86-403a-a101-fa37b5d42b1b` | 2026-10-20 22:50:33 | `56c4e1a11070daad829e6f93cfa95e4b676f24d3d18fa64beaa6449cf7596e20` |
| `water-splitting-200.json` | photocatalytic water splitting | 198 | `07b6d07d-8581-485c-9760-91498bda5680` | 2026-10-20 22:50:42 | `aef29e74797f9deadda52692d313777637b1e68ebf2b8eb805071c9ca4dc6646` |
| `few-shot-200.json` | few-shot learning | 200 | `2d1ab363-4979-488a-b903-211b34029404` | 2026-10-20 22:50:52 | `d26445779ee52f82e5992f7029330c24529e6febfaf51ad33a57c06d0170bd6d` |

**How they were produced:** OpenAlex `search=` (full-text), default relevance order, 2 pages × 100, authenticated;
normalizer v2, identity policy v1 (conservative D06). The first three (the audit topics, used to choose D12/D09 weights) were frozen under relevance policy v1; the last four (**held-out** topics from other fields, added in M14 and never used for tuning) under relevance policy v3. The last three (`nmt`, `crispr`, `urban-heat`, relevance policy v3) are **fresh validation sets** frozen for decision D16 before any landmark setting was chosen: they are used only to validate that choice. The last five (`compressed-sensing`, `differential-privacy`, `topological-insulators`, `water-splitting`, `few-shot`, relevance policy v3) are **fresh validation sets for M4/R7**, frozen on 2026-10-20 before the title constraint was implemented (topics chosen before any of their titles were seen). Each file records its policy versions; comparisons are only made within one dataset. Each file contains
only the snapshot's *optimization candidates* (relevance-eligible and not suppressed as duplicates), with the
fields selection, metrics and ordering need. No abstracts, no credentials.

**Format:** `BenchmarkDataset` format v1 (`backend/src/main/java/dev/researchpath/benchmark/BenchmarkDataset.java`).
Unknown values (e.g. citation counts) are `null`, never zero.

**Regenerate a dataset** (requires the dev DB containing the snapshot): `.\scripts\export-benchmark.ps1 -Id <uuid> -Name <name>`.

**Attribution:** metadata from [OpenAlex](https://openalex.org) (CC0). OpenAlex work ids are kept for every record.
Concept labels and citation counts are provider data as retrieved on 2026-10-18 UTC and may differ today.
