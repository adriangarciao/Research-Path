package dev.researchpath.optimizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** M4 experiment: one paper per title as a feasibility constraint inside greedy selection, and its soft alternative. */
class TitleConstraintTest {

	private static final OptimizerConfig OFF = OptimizerConfig.DEFAULT.withDistinctTitles(false);
	private static final OptimizerConfig ON = OptimizerConfig.DEFAULT.withDistinctTitles(true);

	private static OptimizerCandidate work(String id, int rank, String title, Integer citations, String... concepts) {
		Map<String, Double> cs = new HashMap<>();
		for (String c : concepts) {
			cs.put(c, 1.0);
		}
		return new OptimizerCandidate(id, rank, cs, 1, 0.9, 2000 + rank, false, citations, TitleKey.of(title));
	}

	private static Map<String, Double> weights(String... concepts) {
		Map<String, Double> w = new HashMap<>();
		for (String c : concepts) {
			w.put(c, 1.0 / concepts.length);
		}
		return w;
	}

	@Test
	void aSecondWorkWithTheSameTitleIsInfeasibleAndTheSlotGoesToTheBestOtherWork() {
		List<OptimizerCandidate> pool = List.of(
				work("A1", 1, "Quantum Error Correction", null, "a", "b"),
				work("A2", 2, "quantum error correction.", null, "c", "d"),
				work("B", 3, "Surface codes", null, "c"),
				work("C", 4, "Toric codes", null, "e"));
		Map<String, Double> w = weights("a", "b", "c", "d", "e");
		assertThat(GreedyOptimizer.select(pool, w, 2, Preferences.DEFAULT, OFF).selectedIds()).containsExactly("A1", "A2");

		GreedyOptimizer.Result on = GreedyOptimizer.select(pool, w, 2, Preferences.DEFAULT, ON);
		// Inside selection, not a post-filter: the budget is still filled, by the best remaining gain given A1.
		assertThat(on.selectedIds()).containsExactly("A1", "B");
		assertThat(on.warnings()).isEmpty();
	}

	@Test
	void tooFewDistinctTitlesGiveAShorterPathWithAnExplanation() {
		List<OptimizerCandidate> pool = List.of(
				work("A1", 1, "Differential Privacy", null, "a"),
				work("A2", 2, "Differential privacy", null, "b"),
				work("A3", 3, "DIFFERENTIAL PRIVACY", null, "c"),
				work("B", 4, "The algorithmic foundations of differential privacy", null, "d"));
		GreedyOptimizer.Result r = GreedyOptimizer.select(pool, weights("a", "b", "c", "d"), 3, Preferences.DEFAULT, ON);
		assertThat(r.selectedIds()).hasSize(2).contains("B");
		assertThat(r.warnings()).singleElement().satisfies(w -> assertThat(w)
				.startsWith("FEWER_THAN_REQUESTED: only 2 eligible candidates with distinct titles for a budget of 3")
				.contains("2 left out because a path holds one work per title"));
		// Without the constraint the same pool fills the budget, and the old message is unchanged.
		assertThat(GreedyOptimizer.select(pool, weights("a", "b", "c", "d"), 5, Preferences.DEFAULT, OFF).warnings())
				.containsExactly("FEWER_THAN_REQUESTED: only 4 eligible candidates for a budget of 5");
	}

	@Test
	void untitledWorksAreNeverConstrained() {
		List<OptimizerCandidate> pool = List.of(work("X", 1, null, null, "a"), work("Y", 2, "  ", null, "b"));
		assertThat(GreedyOptimizer.select(pool, weights("a", "b"), 2, Preferences.DEFAULT, ON).selectedIds()).hasSize(2);
	}

	@Test
	void landmarkSeedsSkipASameTitledLandmarkAndTakeTheNextQualifyingWork() {
		List<OptimizerCandidate> pool = List.of(
				work("Q1", 1, "Quantum Error Correction", 9000, "a"),
				work("Q2", 2, "Quantum error correction", 8000, "b"),
				work("S", 3, "Stabilizer codes", 7000, "c"),
				work("T", 4, "Threshold theorem", 10, "d"));
		assertThat(GreedyOptimizer.landmarkSeeds(pool, 10, 2)).extracting(OptimizerCandidate::id).containsExactly("Q1", "Q2");
		assertThat(GreedyOptimizer.landmarkSeeds(pool, 10, 2, true)).extracting(OptimizerCandidate::id)
				.containsExactly("Q1", "S");
		GreedyOptimizer.Result r = GreedyOptimizer.select(pool, weights("a", "b", "c", "d"), 10, Preferences.DEFAULT, ON);
		assertThat(r.decisions().get(0).id()).isEqualTo("Q1");
		assertThat(r.decisions().get(1).id()).isEqualTo("S");
		assertThat(r.decisions().get(1).reason()).isEqualTo(GreedyOptimizer.Reason.LANDMARK);
		assertThat(r.selectedIds()).doesNotContain("Q2");
	}

	@Test
	void resultsAreDeterministicRegardlessOfInputOrder() {
		Random rnd = new Random(4);
		for (int trial = 0; trial < 20; trial++) {
			List<OptimizerCandidate> pool = titledPool(rnd, 14, 6, 4);
			Map<String, Double> w = randomWeights(6);
			List<String> first = GreedyOptimizer.select(pool, w, 6, Preferences.DEFAULT, ON).selectedIds();
			List<OptimizerCandidate> shuffled = new ArrayList<>(pool);
			Collections.shuffle(shuffled, rnd);
			assertThat(GreedyOptimizer.select(shuffled, w, 6, Preferences.DEFAULT, ON).selectedIds()).isEqualTo(first);
		}
	}

	/**
	 * At most one per title plus the budget is a matroid (a truncated partition matroid). For a monotone submodular
	 * objective, greedy under one matroid guarantees 1/2 of the constrained optimum (Fisher, Nemhauser and Wolsey
	 * 1978), not the 1 − 1/e of the cardinality-only case. Asserted: 1/2. Measured and pinned: the observed ratio.
	 */
	@Test
	void greedyIsWithinHalfOfTheConstrainedOptimumAndNeverRepeatsATitle() {
		Random rnd = new Random(2027);
		double worst = 1;
		int optimal = 0;
		int belowCardinalityBound = 0;
		int trials = 80;
		for (int trial = 0; trial < trials; trial++) {
			List<OptimizerCandidate> pool = titledPool(rnd, 11, 6, 2 + rnd.nextInt(5));
			Map<String, Double> w = randomWeights(6);
			int k = 1 + rnd.nextInt(5);
			GreedyOptimizer.Result greedy = GreedyOptimizer.select(pool, w, k, Preferences.DEFAULT, PURE);
			Set<String> titles = new HashSet<>();
			pool.stream().filter(c -> greedy.selectedIds().contains(c.id()))
					.forEach(c -> assertThat(titles.add(c.titleKey())).as("repeated title").isTrue());
			double opt = GreedyOptimizer.exhaustiveOptimum(pool, w, k, Preferences.DEFAULT, PURE);
			double ratio = opt == 0 ? 1 : greedy.objective() / opt;
			assertThat(greedy.objective()).isGreaterThanOrEqualTo(0.5 * opt - 1e-6);
			worst = Math.min(worst, ratio);
			optimal += ratio > 1 - 1e-6 ? 1 : 0;
			belowCardinalityBound += ratio < 1 - 1 / Math.E - 1e-9 ? 1 : 0;
			// The constrained optimum never exceeds the unconstrained one.
			assertThat(opt).isLessThanOrEqualTo(
					GreedyOptimizer.exhaustiveOptimum(pool, w, k, Preferences.DEFAULT, PURE.withDistinctTitles(false)) + 1e-9);
		}
		System.out.printf("title-constrained greedy/exhaustive: worst %.4f, optimal in %d of %d, below 1-1/e in %d%n", worst,
				optimal, trials, belowCardinalityBound);
		assertThat(worst).isGreaterThanOrEqualTo(MEASURED_WORST - 1e-4);
	}

	/** Pure greedy (no landmark seeds, which are placed before greedy and outside this theory) with the constraint. */
	private static final OptimizerConfig PURE = ON.withLandmarkSeeds(0);

	/** Pinned from the seeded run above (a regression check, not a claim). */
	private static final double MEASURED_WORST = 0.9314; // seed 2027: optimal in 65 of 80, never below 1 - 1/e

	@Test
	void softPenaltyKeepsARepeatOnlyWhenItEarnsMoreThanLambdaAndNeverShortensThePath() {
		List<OptimizerCandidate> pool = List.of(
				work("A1", 1, "Differential Privacy", null, "a", "b", "c"),
				work("A2", 2, "Differential privacy", null, "d", "e", "f"),
				work("B", 3, "Local differential privacy", null, "g"));
		Map<String, Double> w = weights("a", "b", "c", "d", "e", "f", "g");
		// After A1, the same-titled A2 adds 3/7 coverage and B 1/7: a small λ keeps the repeat, a large one prefers B.
		assertThat(GreedyOptimizer.select(pool, w, 2, Preferences.DEFAULT, OFF.withTitleRepeatPenalty(0.01)).selectedIds())
				.containsExactly("A1", "A2");
		assertThat(GreedyOptimizer.select(pool, w, 2, Preferences.DEFAULT, OFF.withTitleRepeatPenalty(1.0)).selectedIds())
				.containsExactly("A1", "B");
		// Only same-titled works left: the soft rule fills the budget (the hard one would stop).
		List<OptimizerCandidate> same = List.of(work("A1", 1, "X", null, "a"), work("A2", 2, "x.", null, "b"));
		assertThat(GreedyOptimizer.select(same, weights("a", "b"), 2, Preferences.DEFAULT, OFF.withTitleRepeatPenalty(1.0))
				.selectedIds()).hasSize(2);
		assertThat(GreedyOptimizer.select(same, weights("a", "b"), 2, Preferences.DEFAULT, ON).selectedIds()).hasSize(1);
		for (double bad : new double[] { -0.1, Double.NaN, Double.POSITIVE_INFINITY }) {
			assertThatThrownBy(() -> OFF.withTitleRepeatPenalty(bad)).isInstanceOf(IllegalArgumentException.class);
		}
	}

	// ---- seeded fixtures ----

	private static List<OptimizerCandidate> titledPool(Random rnd, int n, int concepts, int titles) {
		List<OptimizerCandidate> pool = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			Map<String, Double> cs = new HashMap<>();
			int count = 1 + rnd.nextInt(3);
			for (int j = 0; j < count; j++) {
				cs.put("c" + rnd.nextInt(concepts), 0.5 + rnd.nextDouble() * 0.5);
			}
			pool.add(new OptimizerCandidate("W" + i, i + 1, cs, 0.75 + rnd.nextDouble() * 0.25, rnd.nextDouble(),
					1990 + rnd.nextInt(36), rnd.nextInt(5) == 0, rnd.nextInt(5000), "title " + rnd.nextInt(titles)));
		}
		return pool;
	}

	private static Map<String, Double> randomWeights(int concepts) {
		Map<String, Double> w = new HashMap<>();
		double total = 0;
		for (int i = 0; i < concepts; i++) {
			total += Math.log1p(i + 1);
		}
		for (int i = 0; i < concepts; i++) {
			w.put("c" + i, Math.log1p(i + 1) / total);
		}
		return w;
	}

}
