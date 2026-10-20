package dev.researchpath.optimizer;

import dev.researchpath.optimizer.Preferences.Experience;

/**
 * Objective weights (decision D12, PROVISIONAL). Coverage is the primary term (total concept weight 1);
 * {@code qualityShare} caps the whole quality term at that value for a full budget.
 *
 * @param qualityShare β: quality term contributes at most β (vs coverage ≤ 1)
 * @param qualityWeightedCoverage if true, a paper's credit for a concept is its strength × its quality q_p
 *     (low-quality papers become weak coverers); still monotone submodular
 * @param experienceWeight share of the quality weight given to overview-marked works (Beginner) or to works not
 *     marked as overviews (Advanced); 0 for Intermediate. In [0, 0.5].
 * @param landmarkShare λ: share of the influence component taken by the pool-relative raw citation signal
 *     (log(1 + citations) / log(1 + pool max)) instead of the normalized citation percentile. In [0, 1]; 0 = percentile
 *     only (decision D16).
 * @param landmarkSeeds number of reserved landmark slots placed before greedy selection (D16); 0 = none
 */
public record OptimizerConfig(double qualityShare, boolean qualityWeightedCoverage, double experienceWeight,
		double landmarkShare, int landmarkSeeds) {

	/** 2: experience weight lowered to 0.02 (D09); Intermediate selections unchanged. */
	public static final int VERSION = 2;

	/**
	 * D12 (conservative, 2026-10-18): additive objective with β = 4, the smallest β in the sweep that kept
	 * coverage above every baseline on all three datasets while restoring relevance (mean ≥ 0.95) and influence
	 * (median citation percentile ≥ 0.9). β = 0.25 chased niche concepts with barely cited papers. See
	 * docs/evaluation/optimizer-review.md.
	 */
	public static final OptimizerConfig DEFAULT = new OptimizerConfig(4.0, false);

	/**
	 * D09 (conservative, 2026-10-18): the largest swept weight at which Beginner and Advanced selections keep
	 * ≥ 60% of the Intermediate papers on every topic and budget, with overview counts ordered and Advanced still
	 * keeping an overview where Intermediate has several. The previous 0.15 / 0.10 turned Beginner paths into 7–10
	 * overview-marked papers of 10 and removed every overview for Advanced, on a weak title/type cue. See
	 * docs/evaluation/sequence-review.md.
	 */
	public static final double DEFAULT_EXPERIENCE_WEIGHT = 0.02;

	public OptimizerConfig(double qualityShare) {
		this(qualityShare, false);
	}

	/** Landmark share used when none is given (decision D16). */
	public static final double DEFAULT_LANDMARK_SHARE = 0.0;

	/** Landmark seeds used when none is given (decision D16). */
	public static final int DEFAULT_LANDMARK_SEEDS = 0;

	public OptimizerConfig(double qualityShare, boolean qualityWeightedCoverage) {
		this(qualityShare, qualityWeightedCoverage, DEFAULT_EXPERIENCE_WEIGHT);
	}

	public OptimizerConfig(double qualityShare, boolean qualityWeightedCoverage, double experienceWeight) {
		this(qualityShare, qualityWeightedCoverage, experienceWeight, DEFAULT_LANDMARK_SHARE);
	}

	public OptimizerConfig(double qualityShare, boolean qualityWeightedCoverage, double experienceWeight,
			double landmarkShare) {
		this(qualityShare, qualityWeightedCoverage, experienceWeight, landmarkShare, DEFAULT_LANDMARK_SEEDS);
	}

	public OptimizerConfig {
		if (qualityShare < 0) {
			throw new IllegalArgumentException("qualityShare must be >= 0 (keeps the objective monotone)");
		}
		if (experienceWeight < 0 || experienceWeight > 0.5) {
			throw new IllegalArgumentException("experienceWeight must be in [0, 0.5]");
		}
		if (landmarkShare < 0 || landmarkShare > 1) {
			throw new IllegalArgumentException("landmarkShare must be in [0, 1]");
		}
		if (landmarkSeeds < 0 || landmarkSeeds > 5) {
			throw new IllegalArgumentException("landmarkSeeds must be in [0, 5]");
		}
	}

	/** The same configuration with another experience weight (sweeps). */
	public OptimizerConfig withExperienceWeight(double weight) {
		return new OptimizerConfig(qualityShare, qualityWeightedCoverage, weight, landmarkShare, landmarkSeeds);
	}

	/** The same configuration with another quality share and landmark share (sweeps). */
	public OptimizerConfig withQualityAndLandmark(double beta, double landmark) {
		return new OptimizerConfig(beta, qualityWeightedCoverage, experienceWeight, landmark, landmarkSeeds);
	}

	/** The same configuration with another number of landmark seeds (sweeps). */
	public OptimizerConfig withLandmarkSeeds(int seeds) {
		return new OptimizerConfig(qualityShare, qualityWeightedCoverage, experienceWeight, landmarkShare, seeds);
	}

	/** Weights of the quality components; each profile sums to 1 and every weight is non-negative. */
	public record QualityWeights(double relevance, double influence, double recency, double age, double overview,
			double technical) {
	}

	/**
	 * Balanced is time-neutral (recency and age weights equal, and recency + age = 1 for known years);
	 * Foundational favours older influential work; Recent favours newer work. Experience shifts
	 * {@code experienceWeight} to overview-marked works (Beginner) or to works not marked as overviews (Advanced):
	 * accessibility from metadata is a weak signal (D09), so the weight is kept small.
	 */
	public QualityWeights weights(Preferences p) {
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
			overview = experienceWeight;
		} else if (p.experience() == Experience.ADVANCED) {
			technical = experienceWeight;
		}
		double scale = 1 - overview - technical;
		return new QualityWeights(relevance * scale, influence * scale, recency * scale, age * scale, overview,
				technical);
	}

}
