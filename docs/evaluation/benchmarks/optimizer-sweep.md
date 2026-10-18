# Optimizer sensitivity sweep (D12)

Weighted concept coverage of the optimizer vs the best baseline for the same dataset and budget. Δ = optimizer − best baseline. Overlap = papers shared with the best baseline's selection.

## graph neural networks

| Setting | k | Coverage | Best baseline | Δ | Relevance mean | Citation percentile median | Citations median | Year median | OA share | Overlap |
|---|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|
| Intermediate/Balanced additive β=0.0 | 10 | 0.796 | 0.459 (RELEVANCE) | +0.337 | 1.00 | 1.00 | 571.0 | 2021 | 0.70 | 2/10 |
| Intermediate/Balanced additive β=0.25 | 10 | 0.803 | 0.459 (RELEVANCE) | +0.344 | 1.00 | 1.00 | 397.0 | 2021 | 0.80 | 1/10 |
| Intermediate/Balanced additive β=1.0 | 10 | 0.803 | 0.459 (RELEVANCE) | +0.344 | 1.00 | 1.00 | 397.0 | 2021 | 0.80 | 1/10 |
| Intermediate/Balanced additive β=2.0 | 10 | 0.785 | 0.459 (RELEVANCE) | +0.326 | 1.00 | 1.00 | 397.0 | 2021 | 0.70 | 1/10 |
| Intermediate/Balanced additive β=4.0 | 10 | 0.785 | 0.459 (RELEVANCE) | +0.326 | 1.00 | 1.00 | 397.0 | 2021 | 0.70 | 1/10 |
| Intermediate/Balanced additive β=8.0 | 10 | 0.785 | 0.459 (RELEVANCE) | +0.326 | 1.00 | 1.00 | 397.0 | 2021 | 0.70 | 1/10 |
| Intermediate/Balanced QW β=0.0 | 10 | 0.782 | 0.459 (RELEVANCE) | +0.323 | 1.00 | 1.00 | 397.0 | 2021 | 0.70 | 1/10 |
| Intermediate/Balanced QW β=0.25 | 10 | 0.782 | 0.459 (RELEVANCE) | +0.323 | 1.00 | 1.00 | 397.0 | 2021 | 0.70 | 1/10 |
| Intermediate/Balanced QW β=1.0 | 10 | 0.785 | 0.459 (RELEVANCE) | +0.326 | 1.00 | 1.00 | 397.0 | 2021 | 0.70 | 1/10 |
| Intermediate/Balanced QW β=2.0 | 10 | 0.785 | 0.459 (RELEVANCE) | +0.326 | 1.00 | 1.00 | 397.0 | 2021 | 0.70 | 1/10 |
| Intermediate/Balanced QW β=4.0 | 10 | 0.785 | 0.459 (RELEVANCE) | +0.326 | 1.00 | 1.00 | 397.0 | 2021 | 0.70 | 1/10 |
| Intermediate/Balanced QW β=8.0 | 10 | 0.778 | 0.459 (RELEVANCE) | +0.319 | 1.00 | 1.00 | 326.0 | 2022 | 0.70 | 0/10 |
| Beginner/Balanced (chosen) | 10 | 0.636 | 0.459 (RELEVANCE) | +0.177 | 1.00 | 1.00 | 381.5 | 2023 | 0.60 | 1/10 |
| Beginner/Foundational (chosen) | 10 | 0.625 | 0.459 (RELEVANCE) | +0.166 | 1.00 | 1.00 | 472.0 | 2022 | 0.80 | 0/10 |
| Beginner/Recent (chosen) | 10 | 0.610 | 0.459 (RELEVANCE) | +0.151 | 1.00 | 1.00 | 256.5 | 2023 | 0.60 | 1/10 |
| Intermediate/Foundational (chosen) | 10 | 0.726 | 0.459 (RELEVANCE) | +0.267 | 1.00 | 1.00 | 526.5 | 2020 | 0.80 | 1/10 |
| Intermediate/Recent (chosen) | 10 | 0.669 | 0.459 (RELEVANCE) | +0.210 | 1.00 | 1.00 | 260.0 | 2023 | 0.70 | 1/10 |
| Advanced/Balanced (chosen) | 10 | 0.759 | 0.459 (RELEVANCE) | +0.300 | 1.00 | 1.00 | 335.5 | 2021 | 0.60 | 1/10 |
| Advanced/Foundational (chosen) | 10 | 0.700 | 0.459 (RELEVANCE) | +0.241 | 1.00 | 1.00 | 359.0 | 2019 | 0.70 | 1/10 |
| Advanced/Recent (chosen) | 10 | 0.718 | 0.459 (RELEVANCE) | +0.259 | 1.00 | 1.00 | 202.0 | 2022 | 0.70 | 0/10 |
| Intermediate/Balanced additive β=0.0 | 20 | 1.000 | 0.719 (RELEVANCE) | +0.281 | 1.00 | 1.00 | 359.0 | 2020 | 0.70 | 6/20 |
| Intermediate/Balanced additive β=0.25 | 20 | 1.000 | 0.719 (RELEVANCE) | +0.281 | 1.00 | 1.00 | 321.0 | 2021 | 0.70 | 7/20 |
| Intermediate/Balanced additive β=1.0 | 20 | 1.000 | 0.719 (RELEVANCE) | +0.281 | 1.00 | 1.00 | 321.0 | 2021 | 0.65 | 7/20 |
| Intermediate/Balanced additive β=2.0 | 20 | 1.000 | 0.719 (RELEVANCE) | +0.281 | 1.00 | 1.00 | 321.0 | 2021 | 0.65 | 7/20 |
| Intermediate/Balanced additive β=4.0 | 20 | 1.000 | 0.719 (RELEVANCE) | +0.281 | 1.00 | 1.00 | 265.5 | 2021 | 0.65 | 7/20 |
| Intermediate/Balanced additive β=8.0 | 20 | 1.000 | 0.719 (RELEVANCE) | +0.281 | 1.00 | 1.00 | 265.5 | 2021 | 0.65 | 7/20 |
| Intermediate/Balanced QW β=0.0 | 20 | 1.000 | 0.719 (RELEVANCE) | +0.281 | 1.00 | 1.00 | 268.0 | 2021 | 0.60 | 6/20 |
| Intermediate/Balanced QW β=0.25 | 20 | 1.000 | 0.719 (RELEVANCE) | +0.281 | 1.00 | 1.00 | 268.0 | 2021 | 0.60 | 6/20 |
| Intermediate/Balanced QW β=1.0 | 20 | 1.000 | 0.719 (RELEVANCE) | +0.281 | 1.00 | 1.00 | 268.0 | 2021 | 0.60 | 7/20 |
| Intermediate/Balanced QW β=2.0 | 20 | 1.000 | 0.719 (RELEVANCE) | +0.281 | 1.00 | 1.00 | 268.0 | 2021 | 0.60 | 7/20 |
| Intermediate/Balanced QW β=4.0 | 20 | 1.000 | 0.719 (RELEVANCE) | +0.281 | 1.00 | 1.00 | 268.0 | 2021 | 0.60 | 7/20 |
| Intermediate/Balanced QW β=8.0 | 20 | 1.000 | 0.719 (RELEVANCE) | +0.281 | 1.00 | 1.00 | 268.0 | 2021 | 0.60 | 7/20 |
| Beginner/Balanced (chosen) | 20 | 0.859 | 0.719 (RELEVANCE) | +0.140 | 1.00 | 1.00 | 349.0 | 2022 | 0.75 | 4/20 |
| Beginner/Foundational (chosen) | 20 | 0.930 | 0.719 (RELEVANCE) | +0.211 | 1.00 | 1.00 | 371.0 | 2021 | 0.75 | 6/20 |
| Beginner/Recent (chosen) | 20 | 0.784 | 0.719 (RELEVANCE) | +0.065 | 1.00 | 1.00 | 213.0 | 2023 | 0.80 | 2/20 |
| Intermediate/Foundational (chosen) | 20 | 0.986 | 0.719 (RELEVANCE) | +0.267 | 1.00 | 1.00 | 318.0 | 2020 | 0.70 | 8/20 |
| Intermediate/Recent (chosen) | 20 | 0.965 | 0.719 (RELEVANCE) | +0.246 | 1.00 | 1.00 | 260.0 | 2023 | 0.75 | 3/20 |
| Advanced/Balanced (chosen) | 20 | 1.000 | 0.719 (RELEVANCE) | +0.281 | 1.00 | 1.00 | 297.5 | 2020 | 0.60 | 6/20 |
| Advanced/Foundational (chosen) | 20 | 0.986 | 0.719 (RELEVANCE) | +0.267 | 1.00 | 1.00 | 297.5 | 2020 | 0.70 | 7/20 |
| Advanced/Recent (chosen) | 20 | 0.986 | 0.719 (RELEVANCE) | +0.267 | 1.00 | 1.00 | 199.5 | 2022 | 0.70 | 4/20 |

## AI-assisted medical diagnosis

| Setting | k | Coverage | Best baseline | Δ | Relevance mean | Citation percentile median | Citations median | Year median | OA share | Overlap |
|---|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|
| Intermediate/Balanced additive β=0.0 | 10 | 0.921 | 0.546 (RELEVANCE) | +0.375 | 0.88 | 0.82 | 1.0 | 2025 | 1.00 | 0/10 |
| Intermediate/Balanced additive β=0.25 | 10 | 0.889 | 0.546 (RELEVANCE) | +0.343 | 0.90 | 0.75 | 1.5 | 2025 | 1.00 | 1/10 |
| Intermediate/Balanced additive β=1.0 | 10 | 0.854 | 0.546 (RELEVANCE) | +0.308 | 0.90 | 0.88 | 6.0 | 2025 | 1.00 | 2/10 |
| Intermediate/Balanced additive β=2.0 | 10 | 0.817 | 0.546 (RELEVANCE) | +0.271 | 0.95 | 0.98 | 3.5 | 2025 | 0.90 | 3/10 |
| Intermediate/Balanced additive β=4.0 | 10 | 0.684 | 0.546 (RELEVANCE) | +0.138 | 1.00 | 0.98 | 16.0 | 2025 | 0.90 | 4/10 |
| Intermediate/Balanced additive β=8.0 | 10 | 0.652 | 0.546 (RELEVANCE) | +0.106 | 1.00 | 0.98 | 16.0 | 2025 | 0.90 | 5/10 |
| Intermediate/Balanced QW β=0.0 | 10 | 0.854 | 0.546 (RELEVANCE) | +0.308 | 0.88 | 0.74 | 1.5 | 2025 | 0.80 | 2/10 |
| Intermediate/Balanced QW β=0.25 | 10 | 0.836 | 0.546 (RELEVANCE) | +0.290 | 0.93 | 0.89 | 1.5 | 2025 | 0.90 | 2/10 |
| Intermediate/Balanced QW β=1.0 | 10 | 0.817 | 0.546 (RELEVANCE) | +0.271 | 0.95 | 0.98 | 3.5 | 2025 | 0.90 | 3/10 |
| Intermediate/Balanced QW β=2.0 | 10 | 0.684 | 0.546 (RELEVANCE) | +0.138 | 1.00 | 0.98 | 16.0 | 2025 | 0.90 | 4/10 |
| Intermediate/Balanced QW β=4.0 | 10 | 0.684 | 0.546 (RELEVANCE) | +0.138 | 1.00 | 0.98 | 16.0 | 2025 | 0.90 | 4/10 |
| Intermediate/Balanced QW β=8.0 | 10 | 0.652 | 0.546 (RELEVANCE) | +0.106 | 1.00 | 0.98 | 16.0 | 2025 | 0.90 | 5/10 |
| Beginner/Balanced (chosen) | 10 | 0.693 | 0.546 (RELEVANCE) | +0.147 | 0.88 | 0.98 | 31.0 | 2025 | 0.90 | 2/10 |
| Beginner/Foundational (chosen) | 10 | 0.640 | 0.546 (RELEVANCE) | +0.094 | 0.85 | 0.98 | 54.0 | 2025 | 0.80 | 1/10 |
| Beginner/Recent (chosen) | 10 | 0.694 | 0.546 (RELEVANCE) | +0.148 | 0.93 | 0.82 | 1.0 | 2026 | 0.80 | 2/10 |
| Intermediate/Foundational (chosen) | 10 | 0.652 | 0.546 (RELEVANCE) | +0.106 | 0.95 | 0.98 | 16.0 | 2024 | 0.80 | 3/10 |
| Intermediate/Recent (chosen) | 10 | 0.741 | 0.546 (RELEVANCE) | +0.195 | 0.98 | 0.98 | 3.5 | 2025 | 0.90 | 4/10 |
| Advanced/Balanced (chosen) | 10 | 0.684 | 0.546 (RELEVANCE) | +0.138 | 1.00 | 0.98 | 16.0 | 2025 | 0.90 | 4/10 |
| Advanced/Foundational (chosen) | 10 | 0.688 | 0.546 (RELEVANCE) | +0.142 | 0.95 | 0.98 | 16.0 | 2024 | 0.80 | 3/10 |
| Advanced/Recent (chosen) | 10 | 0.684 | 0.546 (RELEVANCE) | +0.138 | 1.00 | 0.98 | 8.5 | 2025 | 0.90 | 4/10 |
| Intermediate/Balanced additive β=0.0 | 20 | 1.000 | 0.665 (PROVIDER_ORDER) | +0.335 | 0.84 | 0.67 | 0.0 | 2025 | 0.90 | 3/20 |
| Intermediate/Balanced additive β=0.25 | 20 | 1.000 | 0.665 (PROVIDER_ORDER) | +0.335 | 0.88 | 0.82 | 3.5 | 2025 | 0.95 | 5/20 |
| Intermediate/Balanced additive β=1.0 | 20 | 1.000 | 0.665 (PROVIDER_ORDER) | +0.335 | 0.89 | 0.88 | 6.0 | 2025 | 1.00 | 5/20 |
| Intermediate/Balanced additive β=2.0 | 20 | 0.970 | 0.665 (PROVIDER_ORDER) | +0.305 | 0.90 | 0.89 | 6.0 | 2025 | 0.95 | 8/20 |
| Intermediate/Balanced additive β=4.0 | 20 | 0.900 | 0.665 (PROVIDER_ORDER) | +0.235 | 0.95 | 0.92 | 8.5 | 2025 | 0.85 | 11/20 |
| Intermediate/Balanced additive β=8.0 | 20 | 0.739 | 0.665 (PROVIDER_ORDER) | +0.074 | 0.99 | 0.97 | 16.0 | 2025 | 0.75 | 12/20 |
| Intermediate/Balanced QW β=0.0 | 20 | 0.985 | 0.665 (PROVIDER_ORDER) | +0.320 | 0.88 | 0.82 | 3.0 | 2025 | 0.90 | 6/20 |
| Intermediate/Balanced QW β=0.25 | 20 | 0.985 | 0.665 (PROVIDER_ORDER) | +0.320 | 0.90 | 0.82 | 3.5 | 2025 | 0.90 | 6/20 |
| Intermediate/Balanced QW β=1.0 | 20 | 0.970 | 0.665 (PROVIDER_ORDER) | +0.305 | 0.91 | 0.97 | 6.0 | 2025 | 0.95 | 7/20 |
| Intermediate/Balanced QW β=2.0 | 20 | 0.935 | 0.665 (PROVIDER_ORDER) | +0.270 | 0.91 | 0.92 | 8.5 | 2025 | 0.90 | 9/20 |
| Intermediate/Balanced QW β=4.0 | 20 | 0.846 | 0.665 (PROVIDER_ORDER) | +0.181 | 0.95 | 0.97 | 16.0 | 2025 | 0.85 | 11/20 |
| Intermediate/Balanced QW β=8.0 | 20 | 0.739 | 0.665 (PROVIDER_ORDER) | +0.074 | 0.99 | 0.97 | 16.0 | 2025 | 0.75 | 12/20 |
| Beginner/Balanced (chosen) | 20 | 0.902 | 0.665 (PROVIDER_ORDER) | +0.237 | 0.89 | 0.96 | 17.0 | 2025 | 0.90 | 6/20 |
| Beginner/Foundational (chosen) | 20 | 0.884 | 0.665 (PROVIDER_ORDER) | +0.219 | 0.86 | 0.98 | 25.0 | 2025 | 0.85 | 4/20 |
| Beginner/Recent (chosen) | 20 | 0.919 | 0.665 (PROVIDER_ORDER) | +0.254 | 0.89 | 0.89 | 16.5 | 2025 | 0.85 | 5/20 |
| Intermediate/Foundational (chosen) | 20 | 0.884 | 0.665 (PROVIDER_ORDER) | +0.219 | 0.91 | 0.95 | 16.0 | 2024 | 0.85 | 8/20 |
| Intermediate/Recent (chosen) | 20 | 0.905 | 0.665 (PROVIDER_ORDER) | +0.240 | 0.95 | 0.88 | 2.0 | 2025 | 0.90 | 10/20 |
| Advanced/Balanced (chosen) | 20 | 0.898 | 0.665 (PROVIDER_ORDER) | +0.233 | 0.95 | 0.92 | 9.5 | 2025 | 0.85 | 11/20 |
| Advanced/Foundational (chosen) | 20 | 0.882 | 0.665 (PROVIDER_ORDER) | +0.217 | 0.93 | 0.95 | 11.0 | 2024 | 0.85 | 9/20 |
| Advanced/Recent (chosen) | 20 | 0.919 | 0.665 (PROVIDER_ORDER) | +0.254 | 0.95 | 0.89 | 3.5 | 2025 | 0.90 | 9/20 |

## reinforcement learning in robotics

| Setting | k | Coverage | Best baseline | Δ | Relevance mean | Citation percentile median | Citations median | Year median | OA share | Overlap |
|---|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|
| Intermediate/Balanced additive β=0.0 | 10 | 0.847 | 0.412 (CITATION) | +0.435 | 1.00 | 0.74 | 1.0 | 2022 | 0.30 | 0/10 |
| Intermediate/Balanced additive β=0.25 | 10 | 0.847 | 0.412 (CITATION) | +0.435 | 1.00 | 0.74 | 1.0 | 2022 | 0.30 | 0/10 |
| Intermediate/Balanced additive β=1.0 | 10 | 0.825 | 0.412 (CITATION) | +0.413 | 1.00 | 0.89 | 14.5 | 2021 | 0.30 | 1/10 |
| Intermediate/Balanced additive β=2.0 | 10 | 0.820 | 0.412 (CITATION) | +0.408 | 1.00 | 0.94 | 14.5 | 2023 | 0.40 | 1/10 |
| Intermediate/Balanced additive β=4.0 | 10 | 0.747 | 0.412 (CITATION) | +0.335 | 1.00 | 0.99 | 70.0 | 2021 | 0.40 | 2/10 |
| Intermediate/Balanced additive β=8.0 | 10 | 0.672 | 0.412 (CITATION) | +0.260 | 1.00 | 1.00 | 85.0 | 2020 | 0.50 | 3/10 |
| Intermediate/Balanced QW β=0.0 | 10 | 0.805 | 0.412 (CITATION) | +0.393 | 1.00 | 0.77 | 5.5 | 2023 | 0.40 | 0/10 |
| Intermediate/Balanced QW β=0.25 | 10 | 0.805 | 0.412 (CITATION) | +0.393 | 1.00 | 0.89 | 23.0 | 2021 | 0.30 | 1/10 |
| Intermediate/Balanced QW β=1.0 | 10 | 0.801 | 0.412 (CITATION) | +0.389 | 1.00 | 0.94 | 33.5 | 2021 | 0.40 | 2/10 |
| Intermediate/Balanced QW β=2.0 | 10 | 0.761 | 0.412 (CITATION) | +0.349 | 1.00 | 0.94 | 62.0 | 2021 | 0.40 | 2/10 |
| Intermediate/Balanced QW β=4.0 | 10 | 0.672 | 0.412 (CITATION) | +0.260 | 1.00 | 0.99 | 66.0 | 2021 | 0.50 | 2/10 |
| Intermediate/Balanced QW β=8.0 | 10 | 0.672 | 0.412 (CITATION) | +0.260 | 1.00 | 1.00 | 85.0 | 2020 | 0.50 | 3/10 |
| Beginner/Balanced (chosen) | 10 | 0.579 | 0.412 (CITATION) | +0.167 | 1.00 | 0.99 | 113.5 | 2022 | 0.70 | 3/10 |
| Beginner/Foundational (chosen) | 10 | 0.503 | 0.412 (CITATION) | +0.091 | 1.00 | 0.99 | 176.0 | 2017 | 0.40 | 4/10 |
| Beginner/Recent (chosen) | 10 | 0.548 | 0.412 (CITATION) | +0.136 | 1.00 | 0.96 | 15.0 | 2025 | 0.80 | 1/10 |
| Intermediate/Foundational (chosen) | 10 | 0.580 | 0.412 (CITATION) | +0.168 | 1.00 | 0.99 | 94.5 | 2013 | 0.30 | 3/10 |
| Intermediate/Recent (chosen) | 10 | 0.783 | 0.412 (CITATION) | +0.371 | 1.00 | 0.89 | 4.5 | 2026 | 0.50 | 1/10 |
| Advanced/Balanced (chosen) | 10 | 0.770 | 0.412 (CITATION) | +0.358 | 1.00 | 0.94 | 33.5 | 2020 | 0.50 | 1/10 |
| Advanced/Foundational (chosen) | 10 | 0.604 | 0.412 (CITATION) | +0.192 | 1.00 | 0.98 | 59.5 | 2009 | 0.20 | 2/10 |
| Advanced/Recent (chosen) | 10 | 0.782 | 0.412 (CITATION) | +0.370 | 1.00 | 0.89 | 7.5 | 2024 | 0.70 | 0/10 |
| Intermediate/Balanced additive β=0.0 | 20 | 1.000 | 0.583 (CITATION) | +0.417 | 1.00 | 0.75 | 9.0 | 2021 | 0.35 | 4/20 |
| Intermediate/Balanced additive β=0.25 | 20 | 1.000 | 0.583 (CITATION) | +0.417 | 1.00 | 0.75 | 9.0 | 2021 | 0.35 | 4/20 |
| Intermediate/Balanced additive β=1.0 | 20 | 1.000 | 0.583 (CITATION) | +0.417 | 1.00 | 0.90 | 18.5 | 2021 | 0.35 | 4/20 |
| Intermediate/Balanced additive β=2.0 | 20 | 0.984 | 0.583 (CITATION) | +0.401 | 1.00 | 0.94 | 28.0 | 2021 | 0.35 | 6/20 |
| Intermediate/Balanced additive β=4.0 | 20 | 0.971 | 0.583 (CITATION) | +0.388 | 1.00 | 0.94 | 51.0 | 2021 | 0.50 | 7/20 |
| Intermediate/Balanced additive β=8.0 | 20 | 0.903 | 0.583 (CITATION) | +0.320 | 1.00 | 0.94 | 51.0 | 2021 | 0.60 | 5/20 |
| Intermediate/Balanced QW β=0.0 | 20 | 0.983 | 0.583 (CITATION) | +0.400 | 1.00 | 0.74 | 4.0 | 2022 | 0.50 | 2/20 |
| Intermediate/Balanced QW β=0.25 | 20 | 0.967 | 0.583 (CITATION) | +0.384 | 1.00 | 0.89 | 14.5 | 2021 | 0.40 | 4/20 |
| Intermediate/Balanced QW β=1.0 | 20 | 0.971 | 0.583 (CITATION) | +0.388 | 1.00 | 0.94 | 25.5 | 2021 | 0.50 | 6/20 |
| Intermediate/Balanced QW β=2.0 | 20 | 0.971 | 0.583 (CITATION) | +0.388 | 1.00 | 0.94 | 25.5 | 2021 | 0.50 | 6/20 |
| Intermediate/Balanced QW β=4.0 | 20 | 0.934 | 0.583 (CITATION) | +0.351 | 1.00 | 0.94 | 58.0 | 2021 | 0.55 | 6/20 |
| Intermediate/Balanced QW β=8.0 | 20 | 0.884 | 0.583 (CITATION) | +0.301 | 1.00 | 0.94 | 58.0 | 2021 | 0.65 | 6/20 |
| Beginner/Balanced (chosen) | 20 | 0.796 | 0.583 (CITATION) | +0.213 | 1.00 | 0.98 | 96.5 | 2021 | 0.55 | 9/20 |
| Beginner/Foundational (chosen) | 20 | 0.830 | 0.583 (CITATION) | +0.247 | 1.00 | 0.98 | 96.5 | 2019 | 0.35 | 9/20 |
| Beginner/Recent (chosen) | 20 | 0.792 | 0.583 (CITATION) | +0.209 | 1.00 | 0.94 | 18.5 | 2025 | 0.70 | 5/20 |
| Intermediate/Foundational (chosen) | 20 | 0.913 | 0.583 (CITATION) | +0.330 | 1.00 | 0.94 | 62.5 | 2015 | 0.20 | 6/20 |
| Intermediate/Recent (chosen) | 20 | 0.953 | 0.583 (CITATION) | +0.370 | 1.00 | 0.93 | 18.5 | 2025 | 0.65 | 2/20 |
| Advanced/Balanced (chosen) | 20 | 0.951 | 0.583 (CITATION) | +0.368 | 1.00 | 0.94 | 28.0 | 2020 | 0.35 | 4/20 |
| Advanced/Foundational (chosen) | 20 | 0.872 | 0.583 (CITATION) | +0.289 | 1.00 | 0.91 | 25.5 | 2013 | 0.25 | 3/20 |
| Advanced/Recent (chosen) | 20 | 0.938 | 0.583 (CITATION) | +0.355 | 1.00 | 0.89 | 7.0 | 2025 | 0.55 | 1/20 |
