package dev.researchpath.optimizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Greedy marginal-gain selection (playbook M09). At each step every remaining candidate's gain is
 * recomputed conditional on the papers already chosen; the largest gain wins, ties broken by quality,
 * then provider rank, then id. Never exceeds the budget or repeats a candidate. With
 * {@link OptimizerConfig#distinctTitles()}, a candidate whose {@link TitleKey} is already selected is infeasible
 * (landmark seeds included), so a path holds at most one work per title (M4). Pure, deterministic,
 * O(k · n · concepts).
 */
public final class GreedyOptimizer {

	public static final int VERSION = 1;

	private GreedyOptimizer() {
	}

	/** Why a paper was chosen, from its recorded contributions. */
	public enum Reason {
		/** It is the first selected paper to carry at least one concept. */
		ADDS_NEW_CONCEPTS,
		/** Adds no new concept; chosen mainly for relevance evidence. */
		HIGH_RELEVANCE,
		/** Chosen mainly for normalized citation influence. */
		INFLUENCE,
		/** Chosen mainly because it fits the requested focus or experience level. */
		PREFERENCE_FIT,
		/** Reserved landmark: among the pool's most-cited works and strongly relevant (D16 landmark seeds). */
		LANDMARK
	}

	/** Landmark seeds are drawn from this many most-cited works of the pool. */
	public static final int LANDMARK_POOL = 5;

	/** Minimum relevance evidence for a landmark seed: all query terms in title/concepts and half in the title. */
	public static final double LANDMARK_MIN_RELEVANCE = 0.75;

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
		List<OptimizerCandidate> seeds = landmarkSeeds(remaining, budget, config.landmarkSeeds(),
				config.distinctTitles() || config.titleRepeatPenalty() > 0);
		int sameTitleExcluded = 0;
		Set<String> selectedTitles = new HashSet<>();
		double penalty = config.titleRepeatPenalty();

		while (selected.size() < budget && !remaining.isEmpty()) {
			OptimizerCandidate best = null;
			double bestGain = -1;
			double bestCoverage = 0;
			double bestQuality = 0;
			boolean seeded = !seeds.isEmpty();
			if (seeded) {
				// Reserved landmark first; its gains are still computed and recorded conditionally.
				best = seeds.remove(0);
				bestCoverage = objective.coverageGain(best, uncovered);
				bestQuality = objective.qualityGain(best);
				bestGain = bestCoverage + bestQuality;
			}
			for (OptimizerCandidate c : seeded ? List.<OptimizerCandidate>of() : remaining) {
				double cov = objective.coverageGain(c, uncovered);
				double qual = objective.qualityGain(c);
				double gain = cov + qual;
				if (penalty > 0 && c.titleKey() != null && selectedTitles.contains(c.titleKey())) {
					gain -= penalty; // soft alternative (M4 experiment): a repeated title must earn more than λ
				}
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
					round(bestQuality), parts, newConcepts,
					seeded ? Reason.LANDMARK : reason(newConcepts, parts, config.weights(preferences))));
			selected.add(best);
			remaining.remove(best);
			if (best.titleKey() != null) {
				selectedTitles.add(best.titleKey());
			}
			if (config.distinctTitles() && best.titleKey() != null) {
				// Infeasible from now on: another work with the same title (kept as a distinct record, never merged).
				String key = best.titleKey();
				int before = remaining.size();
				remaining.removeIf(c -> key.equals(c.titleKey()));
				sameTitleExcluded += before - remaining.size();
			}
			for (Map.Entry<String, Double> e : best.concepts().entrySet()) {
				if (conceptWeights.containsKey(e.getKey())) {
					uncovered.merge(e.getKey(), 1 - objective.strength(best, e.getValue()), (a, b) -> a * b);
				}
			}
		}

		List<String> warnings = new ArrayList<>();
		if (selected.size() < budget) {
			warnings.add(sameTitleExcluded == 0
					? "FEWER_THAN_REQUESTED: only " + selected.size() + " eligible candidates for a budget of " + budget
					: "FEWER_THAN_REQUESTED: only " + selected.size() + " eligible candidates with distinct titles for a "
							+ "budget of " + budget + " (" + sameTitleExcluded + " left out because a path holds one work "
							+ "per title)");
		}
		if (!selected.isEmpty() && selected.stream().allMatch(c -> c.concepts().keySet().stream().noneMatch(conceptWeights::containsKey))) {
			warnings.add("NO_CONCEPT_EVIDENCE: selection driven by quality only");
		}
		return new Result(VERSION, OptimizerConfig.VERSION, preferences, budget, List.copyOf(decisions),
				round(objective.value(selected)), round(objective.coverage(selected)), List.copyOf(warnings));
	}

	/**
	 * D16 landmark seeds: up to {@code seeds} of the pool's {@value #LANDMARK_POOL} most-cited works (known counts,
	 * ties by provider rank) that have strong relevance evidence, at most max(1, budget / 5), in citation order.
	 * With distinct titles, a seed sharing a title with an earlier seed is skipped (the next qualifying work may
	 * take its place).
	 */
	static List<OptimizerCandidate> landmarkSeeds(List<OptimizerCandidate> pool, int budget, int seeds) {
		return landmarkSeeds(pool, budget, seeds, false);
	}

	static List<OptimizerCandidate> landmarkSeeds(List<OptimizerCandidate> pool, int budget, int seeds,
			boolean distinctTitles) {
		if (seeds <= 0 || budget <= 0) {
			return new ArrayList<>();
		}
		int limit = Math.min(seeds, Math.max(1, budget / 5));
		Set<String> titles = new HashSet<>();
		return new ArrayList<>(pool.stream().filter(c -> c.citations() != null)
				.sorted(Comparator.comparingInt((OptimizerCandidate c) -> c.citations()).reversed()
						.thenComparingInt(OptimizerCandidate::providerRank).thenComparing(OptimizerCandidate::id))
				.limit(LANDMARK_POOL)
				.filter(c -> c.relevance() >= LANDMARK_MIN_RELEVANCE)
				.filter(c -> !distinctTitles || c.titleKey() == null || titles.add(c.titleKey()))
				.limit(limit)
				.toList());
	}

	/**
	 * Grounded in recorded facts: a paper that is the first selected one to carry some concept is explained by
	 * the concepts it adds (quality is similar across candidates, so it rarely distinguishes a pick); otherwise
	 * by its largest weighted quality component.
	 */
	private static Reason reason(List<String> newConcepts, Objective.QualityParts q, OptimizerConfig.QualityWeights w) {
		if (!newConcepts.isEmpty()) {
			return Reason.ADDS_NEW_CONCEPTS;
		}
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

	/**
	 * Exhaustive optimum over feasible sets (at most {@code budget} papers and, with distinct titles, one per title) for
	 * tiny pools (tests and offline evaluation only; refuses large inputs).
	 */
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
			Set<String> titles = new HashSet<>();
			boolean feasible = true;
			for (int i = 0; i < n; i++) {
				if ((mask & (1 << i)) != 0) {
					OptimizerCandidate c = pool.get(i);
					s.add(c);
					feasible &= !config.distinctTitles() || c.titleKey() == null || titles.add(c.titleKey());
				}
			}
			if (feasible) {
				best = Math.max(best, objective.value(s));
			}
		}
		return best;
	}

}
