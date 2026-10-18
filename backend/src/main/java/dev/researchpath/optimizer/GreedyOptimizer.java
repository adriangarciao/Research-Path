package dev.researchpath.optimizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Greedy marginal-gain selection (playbook M09). At each step every remaining candidate's gain is
 * recomputed conditional on the papers already chosen; the largest gain wins, ties broken by quality,
 * then provider rank, then id. Never exceeds the budget or repeats a candidate. Pure, deterministic,
 * O(k · n · concepts).
 */
public final class GreedyOptimizer {

	public static final int VERSION = 1;

	private GreedyOptimizer() {
	}

	/** Why a paper was chosen, from its recorded contributions. */
	public enum Reason {
		/** Most of its gain came from concepts not yet represented. */
		ADDS_NEW_CONCEPTS,
		/** Coverage gain was small; chosen mainly for relevance evidence. */
		HIGH_RELEVANCE,
		/** Chosen mainly for normalized citation influence. */
		INFLUENCE,
		/** Chosen mainly because it fits the requested focus or experience level. */
		PREFERENCE_FIT
	}

	/**
	 * One selection step with its numeric contributions.
	 *
	 * @param newConcepts concepts this paper is the first selected paper to carry
	 */
	public record Decision(int step, String id, double marginalGain, double coverageGain, double qualityGain,
			Objective.QualityParts quality, List<String> newConcepts, Reason reason) {
	}

	public record Result(int optimizerVersion, int configVersion, Preferences preferences, int budget,
			List<Decision> decisions, double objective, double coverage, List<String> warnings) {

		public List<String> selectedIds() {
			return decisions.stream().map(Decision::id).toList();
		}
	}

	public static Result select(List<OptimizerCandidate> pool, Map<String, Double> conceptWeights, int budget,
			Preferences preferences, OptimizerConfig config) {
		if (budget < 0) {
			throw new IllegalArgumentException("budget must be >= 0");
		}
		Objective objective = new Objective(conceptWeights, pool, budget, preferences, config);
		List<OptimizerCandidate> remaining = new ArrayList<>(new LinkedHashSet<>(pool));
		remaining.sort(Comparator.comparingInt(OptimizerCandidate::providerRank).thenComparing(OptimizerCandidate::id));
		List<OptimizerCandidate> selected = new ArrayList<>();
		List<Decision> decisions = new ArrayList<>();
		Map<String, Double> uncovered = new TreeMap<>();

		while (selected.size() < budget && !remaining.isEmpty()) {
			OptimizerCandidate best = null;
			double bestGain = -1;
			double bestCoverage = 0;
			double bestQuality = 0;
			for (OptimizerCandidate c : remaining) {
				double cov = objective.coverageGain(c, uncovered);
				double qual = objective.qualityGain(c);
				double gain = cov + qual;
				if (best == null || gain > bestGain + 1e-12
						|| (Math.abs(gain - bestGain) <= 1e-12 && qual > bestQuality + 1e-12)) {
					best = c;
					bestGain = gain;
					bestCoverage = cov;
					bestQuality = qual;
				}
			}
			List<String> newConcepts = best.concepts().keySet().stream()
					.filter(conceptWeights::containsKey)
					.filter(k -> !uncovered.containsKey(k))
					.sorted()
					.toList();
			Objective.QualityParts parts = objective.quality(best);
			decisions.add(new Decision(selected.size() + 1, best.id(), round(bestGain), round(bestCoverage),
					round(bestQuality), parts, newConcepts, reason(bestCoverage, bestQuality, parts, preferences)));
			selected.add(best);
			remaining.remove(best);
			for (Map.Entry<String, Double> e : best.concepts().entrySet()) {
				if (conceptWeights.containsKey(e.getKey())) {
					uncovered.merge(e.getKey(), 1 - Math.max(0, Math.min(1, e.getValue())), (a, b) -> a * b);
				}
			}
		}

		List<String> warnings = new ArrayList<>();
		if (selected.size() < budget) {
			warnings.add("FEWER_THAN_REQUESTED: only " + selected.size() + " eligible candidates for a budget of " + budget);
		}
		if (!selected.isEmpty() && selected.stream().allMatch(c -> c.concepts().keySet().stream().noneMatch(conceptWeights::containsKey))) {
			warnings.add("NO_CONCEPT_EVIDENCE: selection driven by quality only");
		}
		return new Result(VERSION, OptimizerConfig.VERSION, preferences, budget, List.copyOf(decisions),
				round(objective.value(selected)), round(objective.coverage(selected)), List.copyOf(warnings));
	}

	private static Reason reason(double coverageGain, double qualityGain, Objective.QualityParts q, Preferences p) {
		if (coverageGain >= qualityGain) {
			return Reason.ADDS_NEW_CONCEPTS;
		}
		OptimizerConfig.QualityWeights w = OptimizerConfig.weights(p);
		double relevance = w.relevance() * q.relevance();
		double influence = w.influence() * q.influence();
		double fit = w.recency() * q.recency() + w.age() * q.age() + w.overview() * q.overview()
				+ w.technical() * q.technical();
		if (relevance >= influence && relevance >= fit) {
			return Reason.HIGH_RELEVANCE;
		}
		return influence >= fit ? Reason.INFLUENCE : Reason.PREFERENCE_FIT;
	}

	private static double round(double v) {
		return Math.round(v * 1_000_000) / 1_000_000.0;
	}

	/** Exhaustive optimum for tiny pools (tests and offline evaluation only; refuses large inputs). */
	public static double exhaustiveOptimum(List<OptimizerCandidate> pool, Map<String, Double> conceptWeights,
			int budget, Preferences preferences, OptimizerConfig config) {
		if (pool.size() > 16) {
			throw new IllegalArgumentException("exhaustive search is limited to 16 candidates");
		}
		Objective objective = new Objective(conceptWeights, pool, budget, preferences, config);
		double best = 0;
		int n = pool.size();
		for (int mask = 0; mask < (1 << n); mask++) {
			if (Integer.bitCount(mask) > budget) {
				continue;
			}
			List<OptimizerCandidate> s = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				if ((mask & (1 << i)) != 0) {
					s.add(pool.get(i));
				}
			}
			best = Math.max(best, objective.value(s));
		}
		return best;
	}

}
