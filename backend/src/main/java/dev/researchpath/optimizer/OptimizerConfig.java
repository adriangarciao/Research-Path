package dev.researchpath.optimizer;

import dev.researchpath.optimizer.Preferences.Experience;

/**
 * Objective weights (decision D12, PROVISIONAL). Coverage is the primary term (total concept weight 1);
 * {@code qualityShare} caps the whole quality term at that value for a full budget.
 *
 * @param qualityShare β: quality term contributes at most β (vs coverage ≤ 1)
 */
public record OptimizerConfig(double qualityShare) {

	public static final int VERSION = 1;

	/** Placeholder until the D12 sensitivity sweep chooses β (see docs/evaluation/optimizer-review.md). */
	public static final OptimizerConfig DEFAULT = new OptimizerConfig(0.25);

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
