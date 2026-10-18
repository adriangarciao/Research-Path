package dev.researchpath.optimizer;

import dev.researchpath.optimizer.Preferences.Experience;

/**
 * Objective weights (decision D12, PROVISIONAL). Coverage is the primary term (total concept weight 1);
 * {@code qualityShare} caps the whole quality term at that value for a full budget.
 *
 * @param qualityShare β: quality term contributes at most β (vs coverage ≤ 1)
 * @param qualityWeightedCoverage if true, a paper's credit for a concept is its strength × its quality q_p
 *     (low-quality papers become weak coverers); still monotone submodular
 */
public record OptimizerConfig(double qualityShare, boolean qualityWeightedCoverage) {

	public static final int VERSION = 1;

	/**
	 * D12 (conservative, 2026-10-18): additive objective with β = 4, the smallest β in the sweep that kept
	 * coverage above every baseline on all three datasets while restoring relevance (mean ≥ 0.95) and influence
	 * (median citation percentile ≥ 0.9). β = 0.25 chased niche concepts with barely cited papers. See
	 * docs/evaluation/optimizer-review.md.
	 */
	public static final OptimizerConfig DEFAULT = new OptimizerConfig(4.0, false);

	public OptimizerConfig(double qualityShare) {
		this(qualityShare, false);
	}

	public OptimizerConfig {
		if (qualityShare < 0) {
			throw new IllegalArgumentException("qualityShare must be >= 0 (keeps the objective monotone)");
		}
	}

	/** Weights of the quality components; each profile sums to 1 and every weight is non-negative. */
	public record QualityWeights(double relevance, double influence, double recency, double age, double overview,
			double technical) {
	}

	/**
	 * Balanced is time-neutral (recency and age weights equal, and recency + age = 1 for known years);
	 * Foundational favours older influential work; Recent favours newer work. Experience only shifts a small
	 * weight to overview-like works (Beginner) or away from them (Advanced): accessibility from metadata is a
	 * weak signal (D09 OPEN), so its effect is deliberately small.
	 */
	public static QualityWeights weights(Preferences p) {
		double relevance;
		double influence;
		double recency;
		double age;
		switch (p.focus()) {
			case FOUNDATIONAL -> { relevance = 0.40; influence = 0.35; recency = 0.0; age = 0.25; }
			case RECENT -> { relevance = 0.40; influence = 0.20; recency = 0.40; age = 0.0; }
			default -> { relevance = 0.50; influence = 0.30; recency = 0.10; age = 0.10; }
		}
		double overview = 0;
		double technical = 0;
		if (p.experience() == Experience.BEGINNER) {
			overview = 0.15;
		} else if (p.experience() == Experience.ADVANCED) {
			technical = 0.10;
		}
		double scale = 1 - overview - technical;
		return new QualityWeights(relevance * scale, influence * scale, recency * scale, age * scale, overview,
				technical);
	}

}
