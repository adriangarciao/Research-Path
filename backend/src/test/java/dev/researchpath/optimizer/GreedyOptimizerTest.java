package dev.researchpath.optimizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import dev.researchpath.optimizer.Preferences.Experience;
import dev.researchpath.optimizer.Preferences.Focus;

class GreedyOptimizerTest {

	private static final OptimizerConfig SMALL_QUALITY = new OptimizerConfig(0.05);

	private static OptimizerCandidate paper(String id, int rank, Map<String, Double> concepts, double relevance,
			Double influence, Integer year, boolean overview) {
		return new OptimizerCandidate(id, rank, concepts, relevance, influence, year, overview);
	}

	private static Map<String, Double> covers(String... concepts) {
		Map<String, Double> m = new HashMap<>();
		for (String c : concepts) {
			m.put(c, 1.0);
		}
		return m;
	}

	private static Map<String, Double> equalWeights(String... concepts) {
		Map<String, Double> m = new HashMap<>();
		for (String c : concepts) {
			m.put(c, 1.0 / concepts.length);
		}
		return m;
	}

	@Test
	void picksTheLargestMarginalGainNotTheBestIndividualPaper() {
		// Blueprint §5.3: A covers 1-3; B mostly duplicates A but is more influential; C covers 3-5; D covers 6-7.
		Map<String, Double> w = equalWeights("c1", "c2", "c3", "c4", "c5", "c6", "c7");
		List<OptimizerCandidate> pool = List.of(
				paper("A", 1, covers("c1", "c2", "c3"), 1, 0.5, 2020, false),
				paper("B", 2, covers("c1", "c2"), 1, 1.0, 2020, false),
				paper("C", 3, covers("c3", "c4", "c5"), 1, 0.5, 2020, false),
				paper("D", 4, covers("c6", "c7"), 1, 0.2, 2020, false));

		GreedyOptimizer.Result r = GreedyOptimizer.select(pool, w, 3, Preferences.DEFAULT, SMALL_QUALITY);

		assertThat(r.selectedIds()).containsExactly("A", "C", "D");
		assertThat(r.coverage()).isEqualTo(1.0);
		assertThat(r.decisions()).allMatch(d -> d.reason() == GreedyOptimizer.Reason.ADDS_NEW_CONCEPTS);
		assertThat(r.decisions().get(1).newConcepts()).containsExactly("c4", "c5"); // c3 already covered by A
	}

	@Test
	void reasonsAreGroundedInNewConceptsThenTheDominantQualityComponent() {
		Map<String, Double> w = Map.of("a", 1.0);
		List<OptimizerCandidate> pool = List.of(paper("FIRST", 1, covers("a"), 1, 0.2, 2020, false),
				paper("INFLUENTIAL", 2, Map.of(), 0.75, 1.0, 2020, false));
		// Large β: quality dominates gains, but the first paper still adds the concept and is explained by it.
		GreedyOptimizer.Result r = GreedyOptimizer.select(pool, w, 2, Preferences.DEFAULT, new OptimizerConfig(8));
		assertThat(r.decisions()).extracting(GreedyOptimizer.Decision::id, GreedyOptimizer.Decision::reason)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("FIRST", GreedyOptimizer.Reason.ADDS_NEW_CONCEPTS),
						org.assertj.core.groups.Tuple.tuple("INFLUENTIAL", GreedyOptimizer.Reason.HIGH_RELEVANCE));
		assertThat(r.decisions().get(1).newConcepts()).isEmpty();
	}

	@Test
	void coverageSaturatesWhenEveryPaperSharesOneConcept() {
		Map<String, Double> w = Map.of("only", 1.0);
		List<OptimizerCandidate> pool = List.of(paper("P1", 1, Map.of("only", 0.8), 1, 0.5, 2020, false),
				paper("P2", 2, Map.of("only", 0.8), 1, 0.5, 2020, false),
				paper("P3", 3, Map.of("only", 0.8), 1, 0.5, 2020, false));

		GreedyOptimizer.Result r = GreedyOptimizer.select(pool, w, 3, Preferences.DEFAULT, new OptimizerConfig(0));

		assertThat(r.decisions()).extracting(GreedyOptimizer.Decision::coverageGain)
				.containsExactly(0.8, 0.16, 0.032); // 0.8, then 0.8·0.2, then 0.8·0.04: diminishing returns
		assertThat(r.coverage()).isEqualTo(0.992);
	}

	@Test
	void objectiveEqualsTheSumOfRecordedMarginalGains() {
		List<OptimizerCandidate> pool = randomPool(new Random(7), 20, 8);
		Map<String, Double> w = randomWeights(8);
		GreedyOptimizer.Result r = GreedyOptimizer.select(pool, w, 6, Preferences.DEFAULT, OptimizerConfig.DEFAULT);
		double sum = r.decisions().stream().mapToDouble(GreedyOptimizer.Decision::marginalGain).sum();
		assertThat(r.objective()).isCloseTo(sum, org.assertj.core.data.Offset.offset(1e-5));
	}

	@Test
	void objectiveIsMonotoneAndSubmodularOnRandomPools() {
		Random rnd = new Random(42);
		for (int trial = 0; trial < 200; trial++) {
			List<OptimizerCandidate> pool = randomPool(rnd, 12, 6);
			Map<String, Double> w = randomWeights(6);
			Objective f = new Objective(w, pool, 5, randomPreferences(rnd),
					new OptimizerConfig(rnd.nextDouble() * 8, rnd.nextBoolean())); // both coverage modes
			List<OptimizerCandidate> b = new ArrayList<>();
			List<OptimizerCandidate> a = new ArrayList<>();
			for (OptimizerCandidate c : pool.subList(0, 6)) {
				if (rnd.nextBoolean()) {
					b.add(c);
					if (rnd.nextBoolean()) {
						a.add(c); // A ⊆ B
					}
				}
			}
			for (OptimizerCandidate p : pool.subList(6, 12)) {
				double gainA = f.marginalGain(p, a);
				double gainB = f.marginalGain(p, b);
				assertThat(gainB).as("monotone").isGreaterThanOrEqualTo(-1e-12);
				assertThat(gainA).as("submodular: Δ(p|A) ≥ Δ(p|B) for A ⊆ B").isGreaterThanOrEqualTo(gainB - 1e-12);
				List<OptimizerCandidate> bPlus = new ArrayList<>(b);
				bPlus.add(p);
				assertThat(f.value(bPlus)).isCloseTo(f.value(b) + gainB, org.assertj.core.data.Offset.offset(1e-9));
			}
		}
	}

	@Test
	void greedyIsWithinTheGuaranteeOfTheExhaustiveOptimumOnTinyPools() {
		// Fixed explicitly (β = 0.25, experience weight 0.02): the pinned measurement below depends on it.
		OptimizerConfig config = new OptimizerConfig(0.25, false, 0.02);
		Random rnd = new Random(2026);
		double worstRatio = 1;
		int optimal = 0;
		int trials = 60;
		for (int trial = 0; trial < trials; trial++) {
			List<OptimizerCandidate> pool = randomPool(rnd, 10, 7);
			Map<String, Double> w = randomWeights(7);
			int k = 1 + rnd.nextInt(4);
			Preferences prefs = randomPreferences(rnd);
			GreedyOptimizer.Result greedy = GreedyOptimizer.select(pool, w, k, prefs, config);
			double opt = GreedyOptimizer.exhaustiveOptimum(pool, w, k, prefs, config);
			double ratio = opt == 0 ? 1 : greedy.objective() / opt;
			worstRatio = Math.min(worstRatio, ratio);
			if (ratio > 1 - 1e-6) {
				optimal++;
			}
			assertThat(greedy.objective()).isGreaterThanOrEqualTo((1 - 1 / Math.E) * opt - 1e-6);
		}
		// Measured with these seeds (recorded in the optimizer review): worst ratio 0.8990, optimal in 51 of 60
		// (53 of 60 under config v1, whose Beginner/Advanced weights were 0.15/0.10).
		// Pinned as a regression check, not a claim: well above the 0.632 worst-case guarantee asserted above.
		System.out.printf("greedy/exhaustive: worst ratio %.4f, optimal in %d of %d tiny pools%n", worstRatio,
				optimal, trials);
		assertThat(worstRatio).isGreaterThanOrEqualTo(0.8989);
		assertThat(optimal).isGreaterThanOrEqualTo(51);
	}

	@Test
	void guaranteeAlsoHoldsForTheChosenDefaultAndQualityWeightedCoverage() {
		Random rnd = new Random(99);
		for (OptimizerConfig config : List.of(OptimizerConfig.DEFAULT, new OptimizerConfig(4.0, true))) {
			for (int trial = 0; trial < 40; trial++) {
				List<OptimizerCandidate> pool = randomPool(rnd, 10, 7);
				Map<String, Double> w = randomWeights(7);
				int k = 1 + rnd.nextInt(4);
				Preferences prefs = randomPreferences(rnd);
				double greedy = GreedyOptimizer.select(pool, w, k, prefs, config).objective();
				double opt = GreedyOptimizer.exhaustiveOptimum(pool, w, k, prefs, config);
				assertThat(greedy).isGreaterThanOrEqualTo((1 - 1 / Math.E) * opt - 1e-6);
			}
		}
	}

	@Test
	void qualityWeightedCoverageScalesConceptCreditByQuality() {
		OptimizerCandidate strong = paper("S", 1, covers("a"), 1.0, 1.0, 2020, false);
		OptimizerCandidate weak = paper("W", 2, covers("a"), 0.75, 0.0, 2020, false);
		Objective f = new Objective(Map.of("a", 1.0), List.of(strong, weak), 1, Preferences.DEFAULT,
				new OptimizerConfig(0, true));
		assertThat(f.coverage(List.of(strong))).isGreaterThan(f.coverage(List.of(weak)));
		assertThat(f.strength(weak, 1.0)).isEqualTo(f.quality(weak).total());
	}

	@Test
	void exhaustiveSearchRefusesLargePools() {
		assertThatThrownBy(() -> GreedyOptimizer.exhaustiveOptimum(randomPool(new Random(1), 17, 3), randomWeights(3), 2,
				Preferences.DEFAULT, OptimizerConfig.DEFAULT)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void missingDataDoesNotCrashAndUnknownIsNeutral() {
		OptimizerCandidate unknown = paper("U", 1, Map.of(), 0.75, null, null, false);
		Objective f = new Objective(Map.of(), List.of(unknown), 1, Preferences.DEFAULT, OptimizerConfig.DEFAULT);
		assertThat(f.quality(unknown).influence()).isEqualTo(0.5);
		assertThat(f.quality(unknown).recency()).isEqualTo(0.5);

		GreedyOptimizer.Result r = GreedyOptimizer.select(List.of(unknown), Map.of(), 1, Preferences.DEFAULT,
				OptimizerConfig.DEFAULT);
		assertThat(r.selectedIds()).containsExactly("U");
		assertThat(r.warnings()).anyMatch(w -> w.startsWith("NO_CONCEPT_EVIDENCE"));
	}

	@Test
	void emptyPoolsBudgetsAndDuplicatesAreHandled() {
		assertThat(GreedyOptimizer.select(List.of(), Map.of(), 5, Preferences.DEFAULT, OptimizerConfig.DEFAULT).warnings())
				.anyMatch(w -> w.startsWith("FEWER_THAN_REQUESTED"));
		OptimizerCandidate p = paper("P", 1, covers("x"), 1, 0.5, 2020, false);
		GreedyOptimizer.Result r = GreedyOptimizer.select(List.of(p, p, p), Map.of("x", 1.0), 3, Preferences.DEFAULT,
				OptimizerConfig.DEFAULT);
		assertThat(r.selectedIds()).containsExactly("P"); // a duplicate input never appears twice
		assertThat(GreedyOptimizer.select(List.of(p), Map.of("x", 1.0), 0, Preferences.DEFAULT, OptimizerConfig.DEFAULT)
				.selectedIds()).isEmpty();
		assertThatThrownBy(() -> GreedyOptimizer.select(List.of(p), Map.of(), -1, Preferences.DEFAULT,
				OptimizerConfig.DEFAULT)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void tiesAreBrokenDeterministicallyRegardlessOfInputOrder() {
		List<OptimizerCandidate> pool = new ArrayList<>(List.of(paper("Z", 3, covers("a"), 1, 0.5, 2020, false),
				paper("Y", 1, covers("a"), 1, 0.5, 2020, false), paper("X", 2, covers("a"), 1, 0.5, 2020, false)));
		List<String> first = GreedyOptimizer.select(pool, Map.of("a", 1.0), 2, Preferences.DEFAULT, OptimizerConfig.DEFAULT).selectedIds();
		java.util.Collections.reverse(pool);
		List<String> second = GreedyOptimizer.select(pool, Map.of("a", 1.0), 2, Preferences.DEFAULT, OptimizerConfig.DEFAULT).selectedIds();
		assertThat(first).isEqualTo(second).containsExactly("Y", "X"); // equal gains → provider rank
	}

	@Test
	void preferencesShiftChoicesWithinTheSameAlgorithm() {
		// Two papers with identical coverage; one old and influential, one recent.
		List<OptimizerCandidate> pool = List.of(paper("OLD", 1, covers("a"), 1, 0.9, 2005, false),
				paper("NEW", 2, covers("a"), 1, 0.4, 2025, false),
				paper("SURVEY", 3, covers("a"), 1, 0.5, 2015, true));
		OptimizerConfig strong = new OptimizerConfig(1.0);
		assertThat(GreedyOptimizer.select(pool, Map.of("a", 1.0), 1, new Preferences(Experience.INTERMEDIATE, Focus.RECENT), strong)
				.selectedIds()).containsExactly("NEW");
		assertThat(GreedyOptimizer.select(pool, Map.of("a", 1.0), 1, new Preferences(Experience.INTERMEDIATE, Focus.FOUNDATIONAL), strong)
				.selectedIds()).containsExactly("OLD");
		Objective f = new Objective(Map.of("a", 1.0), pool, 1, new Preferences(Experience.BEGINNER, Focus.BALANCED), strong);
		assertThat(f.quality(pool.get(2)).total()).isGreaterThan(
				new Objective(Map.of("a", 1.0), pool, 1, Preferences.DEFAULT, strong).quality(pool.get(2)).total());
	}

	@Test
	void balancedFocusIsTimeNeutralForKnownYears() {
		OptimizerConfig.QualityWeights w = OptimizerConfig.DEFAULT.weights(Preferences.DEFAULT);
		assertThat(w.recency()).isEqualTo(w.age());
		for (Preferences.Experience e : Preferences.Experience.values()) {
			for (Preferences.Focus fo : Preferences.Focus.values()) {
				OptimizerConfig.QualityWeights q = OptimizerConfig.DEFAULT.weights(new Preferences(e, fo));
				double sum = q.relevance() + q.influence() + q.recency() + q.age() + q.overview() + q.technical();
				assertThat(sum).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
			}
		}
	}

	@Test
	void landmarkSignalIsPoolRelativeLogCitationsAndNeutralWhenUnknown() {
		List<OptimizerCandidate> pool = List.of(
				new OptimizerCandidate("BIG", 1, covers("a"), 1, 1.0, 2020, false, 40_000),
				new OptimizerCandidate("MID", 2, covers("a"), 1, 1.0, 2020, false, 400),
				new OptimizerCandidate("NONE", 3, covers("a"), 1, 1.0, 2020, false, 0),
				new OptimizerCandidate("UNKNOWN", 4, covers("a"), 1, 1.0, 2020, false, null));
		Objective f = new Objective(Map.of("a", 1.0), pool, 1, Preferences.DEFAULT, new OptimizerConfig(4, false, 0.02, 0.5));
		assertThat(f.landmark(pool.get(0))).isEqualTo(1.0);
		assertThat(f.landmark(pool.get(1))).isCloseTo(Math.log1p(400) / Math.log1p(40_000), org.assertj.core.data.Offset.offset(1e-12));
		assertThat(f.landmark(pool.get(2))).isZero();
		assertThat(f.landmark(pool.get(3))).isEqualTo(0.5);
	}

	@Test
	void landmarkShareSeparatesPapersTheSaturatedPercentileCannot() {
		// Identical coverage and the same (saturated) percentile: only raw citations differ.
		List<OptimizerCandidate> pool = List.of(
				new OptimizerCandidate("FEW", 1, covers("a"), 1, 0.99, 2020, false, 300),
				new OptimizerCandidate("LANDMARK", 2, covers("a"), 1, 0.99, 2020, false, 43_000));
		assertThat(GreedyOptimizer.select(pool, Map.of("a", 1.0), 1, Preferences.DEFAULT, new OptimizerConfig(4, false, 0.02, 0))
				.selectedIds()).as("percentile only: tie broken by provider rank").containsExactly("FEW");
		assertThat(GreedyOptimizer.select(pool, Map.of("a", 1.0), 1, Preferences.DEFAULT, new OptimizerConfig(4, false, 0.02, 0.5))
				.selectedIds()).containsExactly("LANDMARK");
	}

	@Test
	void guaranteeHoldsWithTheLandmarkSignal() {
		// The landmark term is modular and non-negative, so the objective stays monotone submodular.
		Random rnd = new Random(16);
		for (int trial = 0; trial < 30; trial++) {
			List<OptimizerCandidate> pool = new ArrayList<>();
			for (OptimizerCandidate c : randomPool(rnd, 9, 6)) {
				pool.add(new OptimizerCandidate(c.id(), c.providerRank(), c.concepts(), c.relevance(), c.influence(), c.year(),
						c.overviewLike(), rnd.nextInt(50_000)));
			}
			Map<String, Double> w = randomWeights(6);
			int k = 1 + rnd.nextInt(4);
			OptimizerConfig config = new OptimizerConfig(4, false, 0.02, rnd.nextDouble());
			GreedyOptimizer.Result greedy = GreedyOptimizer.select(pool, w, k, Preferences.DEFAULT, config);
			double opt = GreedyOptimizer.exhaustiveOptimum(pool, w, k, Preferences.DEFAULT, config);
			assertThat(greedy.objective()).isGreaterThanOrEqualTo((1 - 1 / Math.E) * opt - 1e-6);
		}
		assertThatThrownBy(() -> new OptimizerConfig(4, false, 0.02, 1.5)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void landmarkSeedsReserveStronglyRelevantMostCitedWorksFirst() {
		List<OptimizerCandidate> pool = List.of(
				new OptimizerCandidate("BROAD", 1, covers("a", "b", "c"), 1, 0.9, 2020, false, 50),
				new OptimizerCandidate("CLASSIC", 2, covers("a"), 1, 0.99, 2010, false, 40_000),
				new OptimizerCandidate("OFFTOPIC", 3, covers("d"), 0.5, 0.99, 2012, false, 90_000),
				new OptimizerCandidate("OTHER", 4, covers("d"), 1, 0.9, 2021, false, 10));
		Map<String, Double> w = equalWeights("a", "b", "c", "d");
		GreedyOptimizer.Result none = GreedyOptimizer.select(pool, w, 2, Preferences.DEFAULT, OptimizerConfig.DEFAULT);
		assertThat(none.selectedIds()).doesNotContain("CLASSIC");

		GreedyOptimizer.Result seeded = GreedyOptimizer.select(pool, w, 2, Preferences.DEFAULT,
				OptimizerConfig.DEFAULT.withLandmarkSeeds(2));
		// At most max(1, k / 5) = 1 seed for k = 2; the weakly relevant 90k-citation work is never a seed.
		assertThat(seeded.decisions().get(0).id()).isEqualTo("CLASSIC");
		assertThat(seeded.decisions().get(0).reason()).isEqualTo(GreedyOptimizer.Reason.LANDMARK);
		assertThat(seeded.decisions().get(1).reason()).isNotEqualTo(GreedyOptimizer.Reason.LANDMARK);
		assertThat(seeded.objective()).isCloseTo(seeded.decisions().stream().mapToDouble(GreedyOptimizer.Decision::marginalGain).sum(),
				org.assertj.core.data.Offset.offset(1e-5));
		assertThat(GreedyOptimizer.landmarkSeeds(pool, 10, 3)).extracting(OptimizerCandidate::id).containsExactly("CLASSIC", "BROAD");
		assertThat(GreedyOptimizer.landmarkSeeds(pool, 10, 0)).isEmpty();
		assertThatThrownBy(() -> OptimizerConfig.DEFAULT.withLandmarkSeeds(6)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void negativeQualityShareIsRejectedToKeepTheObjectiveMonotone() {
		assertThatThrownBy(() -> new OptimizerConfig(-0.1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new OptimizerConfig(1, false, 0.6)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new OptimizerConfig(1, false, -0.01)).isInstanceOf(IllegalArgumentException.class);
	}

	// ---- random fixtures (seeded, reproducible) ----

	private static List<OptimizerCandidate> randomPool(Random rnd, int n, int concepts) {
		List<OptimizerCandidate> pool = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			Map<String, Double> cs = new HashMap<>();
			int count = rnd.nextInt(4);
			for (int j = 0; j < count; j++) {
				cs.put("c" + rnd.nextInt(concepts), 0.5 + rnd.nextDouble() * 0.5);
			}
			pool.add(new OptimizerCandidate("W" + i, i + 1, cs, 0.75 + rnd.nextDouble() * 0.25,
					rnd.nextInt(5) == 0 ? null : rnd.nextDouble(), rnd.nextInt(6) == 0 ? null : 1990 + rnd.nextInt(36),
					rnd.nextInt(5) == 0));
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

	private static Preferences randomPreferences(Random rnd) {
		return new Preferences(Experience.values()[rnd.nextInt(3)], Focus.values()[rnd.nextInt(3)]);
	}

}
