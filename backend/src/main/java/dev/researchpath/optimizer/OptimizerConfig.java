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
 * @param distinctTitles at most one paper per {@link TitleKey} in a selection, a feasibility constraint inside greedy
 *     selection (decision D18, release R7)
 * @param titleRepeatPenalty λ ≥ 0: soft alternative to {@code distinctTitles}, evaluated for D18 and not adopted; a
 *     candidate whose title
 *     key is already selected has its greedy gain reduced by λ instead of being infeasible; 0 = off
 */
public record OptimizerConfig(double qualityShare, boolean qualityWeightedCoverage, double experienceWeight,
		double landmarkShare, int landmarkSeeds, boolean distinctTitles, double titleRepeatPenalty) {

	/**
	 * 2: experience weight lowered to 0.02 (D09); Intermediate selections unchanged.
	 * 3: two landmark seeds by default (D16).
	 * 4: at most one paper per title (D18).
	 */
	public static final int VERSION = 4;

	/**
	 * D18 (owner decision, 2026-10-20): at most one paper per conservative title key, as a feasibility constraint
	 * inside greedy selection. Removed all 19 repeated titles in 60 benchmark selections; relevance, landmarks at
	 * k = 10, budgets and runtime unchanged; the owner accepted the pre-registered limits being exceeded on one fresh
	 * topic (coverage −0.031, redundancy +0.014) and one quantum k = 5 path losing its only citation link. Greedy's
	 * worst-case guarantee under this matroid constraint is 1/2 (1 − 1/e when no title repeats). See
	 * docs/evaluation/title-diversity.md.
	 */
	public static final boolean DEFAULT_DISTINCT_TITLES = true;

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

	/**
	 * D16 (owner decision, 2026-10-19): two reserved landmark slots (at most max(1, k / 5)) for strongly relevant
	 * most-cited works. Keeps a mean of 2.33 of each pool's 5 most-cited works at k = 10 on the audit topics (was 0.67)
	 * and 3.0 on fresh validation topics (was 1.67); costs at most 0.061 weighted coverage (the pre-registered limit
	 * was 0.05, exceeded in 3 of 14 topic/budget cases, accepted by the owner). See docs/evaluation/BENCHMARKS.md.
	 */
	public static final int DEFAULT_LANDMARK_SEEDS = 2;

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

	public OptimizerConfig(double qualityShare, boolean qualityWeightedCoverage, double experienceWeight,
			double landmarkShare, int landmarkSeeds) {
		this(qualityShare, qualityWeightedCoverage, experienceWeight, landmarkShare, landmarkSeeds,
				DEFAULT_DISTINCT_TITLES);
	}

	public OptimizerConfig(double qualityShare, boolean qualityWeightedCoverage, double experienceWeight,
			double landmarkShare, int landmarkSeeds, boolean distinctTitles) {
		this(qualityShare, qualityWeightedCoverage, experienceWeight, landmarkShare, landmarkSeeds, distinctTitles, 0);
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
		if (!(titleRepeatPenalty >= 0) || Double.isInfinite(titleRepeatPenalty)) {
			throw new IllegalArgumentException("titleRepeatPenalty must be finite and >= 0");
		}
	}

	/** The same configuration with another experience weight (sweeps). */
	public OptimizerConfig withExperienceWeight(double weight) {
		return new OptimizerConfig(qualityShare, qualityWeightedCoverage, weight, landmarkShare, landmarkSeeds,
				distinctTitles, titleRepeatPenalty);
	}

	/** The same configuration with another quality share and landmark share (sweeps). */
	public OptimizerConfig withQualityAndLandmark(double beta, double landmark) {
		return new OptimizerConfig(beta, qualityWeightedCoverage, experienceWeight, landmark, landmarkSeeds,
				distinctTitles, titleRepeatPenalty);
	}

	/** The same configuration with another number of landmark seeds (sweeps). */
	public OptimizerConfig withLandmarkSeeds(int seeds) {
		return new OptimizerConfig(qualityShare, qualityWeightedCoverage, experienceWeight, landmarkShare, seeds,
				distinctTitles, titleRepeatPenalty);
	}

	/** The same configuration with or without the one-paper-per-title constraint (M4 comparison). */
	public OptimizerConfig withDistinctTitles(boolean distinct) {
		return new OptimizerConfig(qualityShare, qualityWeightedCoverage, experienceWeight, landmarkShare, landmarkSeeds,
				distinct, titleRepeatPenalty);
	}

	/** The same configuration with a soft title-repeat penalty λ (and no hard constraint): evaluated, not adopted. */
	public OptimizerConfig withTitleRepeatPenalty(double penalty) {
		return new OptimizerConfig(qualityShare, qualityWeightedCoverage, experienceWeight, landmarkShare, landmarkSeeds,
				false, penalty);
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
