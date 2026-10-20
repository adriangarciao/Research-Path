package dev.researchpath.optimizer;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * F(S) = Σ_c w_c · (1 − Π_{p∈S} (1 − s_{p,c})) + (β / k) · Σ_{p∈S} q_p.
 *
 * <p>The coverage term is probabilistic weighted coverage (monotone submodular: each further paper on a concept
 * adds less); the quality term is modular with q_p ≥ 0. Their non-negative sum is monotone submodular, so greedy
 * selection under a cardinality budget has the standard (1 − 1/e) guarantee for this objective. Pure and
 * deterministic.
 */
public final class Objective {

	private final Map<String, Double> conceptWeights;
	private final OptimizerConfig.QualityWeights qualityWeights;
	private final double qualityScale;
	private final boolean qualityWeightedCoverage;
	private final int minYear;
	private final int maxYear;
	private final double landmarkShare;
	private final double maxLogCitations;

	/**
	 * @param pool all candidates (fixes year normalization for this pool)
	 * @param budget k, used to normalize the quality term (β / k per paper)
	 */
	public Objective(Map<String, Double> conceptWeights, Collection<OptimizerCandidate> pool, int budget,
			Preferences preferences, OptimizerConfig config) {
		this.conceptWeights = Map.copyOf(conceptWeights);
		this.qualityWeights = config.weights(preferences);
		this.qualityScale = budget <= 0 ? 0 : config.qualityShare() / budget;
		this.qualityWeightedCoverage = config.qualityWeightedCoverage();
		this.minYear = pool.stream().map(OptimizerCandidate::year).filter(y -> y != null).mapToInt(Integer::intValue)
				.min().orElse(0);
		this.maxYear = pool.stream().map(OptimizerCandidate::year).filter(y -> y != null).mapToInt(Integer::intValue)
				.max().orElse(0);
		this.landmarkShare = config.landmarkShare();
		this.maxLogCitations = Math.log1p(pool.stream().map(OptimizerCandidate::citations).filter(c -> c != null)
				.mapToInt(Integer::intValue).max().orElse(0));
	}

	/** Quality components in [0, 1]; unknown inputs are neutral (0.5). */
	public record QualityParts(double relevance, double influence, double recency, double age, double overview,
			double technical, double total) {
	}

	public QualityParts quality(OptimizerCandidate c) {
		double relevance = clamp(c.relevance());
		double percentile = c.influence() == null ? 0.5 : clamp(c.influence());
		double influence = landmarkShare == 0 ? percentile
				: (1 - landmarkShare) * percentile + landmarkShare * landmark(c);
		double recency = c.year() == null || maxYear == minYear ? 0.5
				: (c.year() - minYear) / (double) (maxYear - minYear);
		double age = 1 - recency;
		double overview = c.overviewLike() ? 1 : 0;
		double technical = c.overviewLike() ? 0 : 1;
		OptimizerConfig.QualityWeights w = qualityWeights;
		double total = w.relevance() * relevance + w.influence() * influence + w.recency() * recency + w.age() * age
				+ w.overview() * overview + w.technical() * technical;
		return new QualityParts(relevance, influence, recency, age, overview, technical, total);
	}

	/**
	 * Pool-relative citation standing in [0, 1]: log(1 + citations) / log(1 + most citations in the pool). Unlike the
	 * normalized percentile it does not saturate, so a landmark with 40,000 citations outranks one with 400 (D16).
	 * Unknown counts are neutral (0.5).
	 */
	public double landmark(OptimizerCandidate c) {
		if (c.citations() == null || maxLogCitations == 0) {
			return 0.5;
		}
		return clamp(Math.log1p(Math.max(0, c.citations())) / maxLogCitations);
	}

	/** Contribution of one paper's quality to F (β / k · q_p). */
	public double qualityGain(OptimizerCandidate c) {
		return qualityScale * quality(c).total();
	}

	/** Weighted coverage of a set, in [0, 1] when concept weights sum to 1. */
	public double coverage(Collection<OptimizerCandidate> selected) {
		Map<String, Double> uncovered = uncoveredProbability(selected);
		double sum = 0;
		for (Map.Entry<String, Double> e : conceptWeights.entrySet()) {
			sum += e.getValue() * (1 - uncovered.getOrDefault(e.getKey(), 1.0));
		}
		return sum;
	}

	public double value(Collection<OptimizerCandidate> selected) {
		double quality = 0;
		for (OptimizerCandidate c : selected) {
			quality += qualityGain(c);
		}
		return coverage(selected) + quality;
	}

	/** Π_{p∈S} (1 − s_{p,c}) per concept: probability the concept is still not represented. */
	public Map<String, Double> uncoveredProbability(Collection<OptimizerCandidate> selected) {
		Map<String, Double> uncovered = new HashMap<>();
		for (OptimizerCandidate c : selected) {
			for (Map.Entry<String, Double> e : c.concepts().entrySet()) {
				if (conceptWeights.containsKey(e.getKey())) {
					uncovered.merge(e.getKey(), 1 - strength(c, e.getValue()), (a, b) -> a * b);
				}
			}
		}
		return uncovered;
	}

	/** Coverage gain of adding {@code c} given the current uncovered probabilities. */
	public double coverageGain(OptimizerCandidate c, Map<String, Double> uncovered) {
		double gain = 0;
		for (Map.Entry<String, Double> e : c.concepts().entrySet()) {
			Double w = conceptWeights.get(e.getKey());
			if (w != null) {
				gain += w * strength(c, e.getValue()) * uncovered.getOrDefault(e.getKey(), 1.0);
			}
		}
		return gain;
	}

	public double marginalGain(OptimizerCandidate c, List<OptimizerCandidate> selected) {
		return coverageGain(c, uncoveredProbability(selected)) + qualityGain(c);
	}

	/** Probability that paper c represents a concept: its confidence, times its quality if configured. */
	public double strength(OptimizerCandidate c, double confidence) {
		double s = clamp(confidence);
		return qualityWeightedCoverage ? s * quality(c).total() : s;
	}

	private static double clamp(double v) {
		return Math.max(0, Math.min(1, v));
	}

}
