package dev.researchpath.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.researchpath.optimizer.GreedyOptimizer;
import dev.researchpath.optimizer.OptimizerConfig;
import dev.researchpath.optimizer.Preferences;

/** M09: the optimizer on the committed frozen datasets, compared with baselines on identical inputs. */
class OptimizerBenchmarkTest {

	static BenchmarkDataset load(String name) throws Exception {
		return DatasetIo.read(RealDatasetBenchmarkTest.DIR.resolve(name + ".json"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "gnn-200", "rl-robotics-200", "medical-ai-200" })
	void optimizerSelectionsAreValidExplainedAndDeterministic(String name) throws Exception {
		BenchmarkDataset d = load(name);
		OptimizerSelector selector = new OptimizerSelector(Preferences.DEFAULT, OptimizerConfig.DEFAULT);
		for (int k : BenchmarkRunner.BUDGETS) {
			GreedyOptimizer.Result a = selector.optimize(d, k);
			GreedyOptimizer.Result b = selector.optimize(d, k);
			assertThat(a).isEqualTo(b);
			assertThat(a.selectedIds()).hasSize(Math.min(k, d.candidates().size())).doesNotHaveDuplicates();
			assertThat(a.decisions()).allSatisfy(dec -> {
				assertThat(dec.marginalGain()).isGreaterThanOrEqualTo(0);
				assertThat(dec.reason()).isNotNull();
			});
			// Greedy gains are non-increasing for a monotone submodular objective with a fixed modular term.
			List<GreedyOptimizer.Decision> ds = a.decisions();
			for (int i = 1; i < ds.size(); i++) {
				assertThat(ds.get(i).coverageGain()).isLessThanOrEqualTo(ds.get(i - 1).marginalGain() + 1e-9);
			}
		}
	}

	/**
	 * The D12 decision criteria, enforced on the committed datasets for the default configuration so a future
	 * change cannot silently regress them: coverage above the best baseline, mean relevance ≥ 0.95 and median
	 * citation percentile ≥ 0.9 for every topic at k = 10 and 20.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "gnn-200", "rl-robotics-200", "medical-ai-200" })
	void defaultConfigurationMeetsTheD12Criteria(String name) throws Exception {
		BenchmarkDataset d = load(name);
		var model = SelectionMetrics.conceptModel(d);
		for (int k : List.of(10, 20)) {
			double bestBaseline = Baselines.ALL.stream()
					.mapToDouble(b -> SelectionMetrics.of(d, model, k, b.select(d, k)).weightedCoverage()).max().orElseThrow();
			var selection = new OptimizerSelector(Preferences.DEFAULT, OptimizerConfig.DEFAULT).select(d, k);
			SelectionMetrics m = SelectionMetrics.of(d, model, k, selection);
			assertThat(m.weightedCoverage()).as("%s k=%d coverage", name, k).isGreaterThan(bestBaseline);
			assertThat(m.relevanceMean()).as("%s k=%d relevance", name, k).isGreaterThanOrEqualTo(0.95);
			assertThat(SensitivitySweep.medianPercentile(selection)).as("%s k=%d influence", name, k)
					.isGreaterThanOrEqualTo(0.9);
		}
	}

	/** Selected ids per budget for the default configuration; later milestones must not change them silently. */
	static final java.util.Map<String, String> OPTIMIZER_FINGERPRINTS = java.util.Map.of(
			"gnn-200", "326e12a70438b56ca050b9ffa0f71e82b5c9094483c86e3e88399222758e030d", // M4 experiment: one paper changes at k = 20
			"rl-robotics-200", "a1d890214046daa407c3470c98612f89aa27dc21b72357b0f5daa16765b578bb",
			"medical-ai-200", "e80f92b85208ec852ead57e00739fe6175469d79b56b580f1a1ab4bfc2bbe5fb");

	@ParameterizedTest
	@ValueSource(strings = { "gnn-200", "rl-robotics-200", "medical-ai-200" })
	void optimizerSelectionsMatchThePinnedFingerprint(String name) throws Exception {
		BenchmarkDataset d = load(name);
		StringBuilder sb = new StringBuilder();
		for (int k : BenchmarkRunner.BUDGETS) {
			sb.append(k).append(':').append(new OptimizerSelector(Preferences.DEFAULT, OptimizerConfig.DEFAULT)
					.optimize(d, k).selectedIds()).append('\n');
		}
		String actual = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
				.digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		if (!actual.equals(OPTIMIZER_FINGERPRINTS.get(name))) {
			Path out = Path.of("target", "optimizer-fingerprint-" + name + ".txt");
			Files.createDirectories(out.getParent());
			Files.writeString(out, sb.toString());
		}
		assertThat(actual).as("optimizer selections changed for %s", name).isEqualTo(OPTIMIZER_FINGERPRINTS.get(name));
	}

	@Test
	void runtimeStaysSmallForTheLargestBudget() throws Exception {
		BenchmarkDataset d = load("rl-robotics-200");
		OptimizerSelector selector = new OptimizerSelector(Preferences.DEFAULT, OptimizerConfig.DEFAULT);
		selector.select(d, 20); // warm-up
		long start = System.nanoTime();
		selector.select(d, 20);
		long millis = (System.nanoTime() - start) / 1_000_000;
		assertThat(millis).as("200 candidates, k=20").isLessThan(2_000);
	}

	@Test
	void sweepWritesAReadableReport() throws Exception {
		List<SensitivitySweep.Row> rows = new ArrayList<>();
		for (String n : List.of("gnn-200", "rl-robotics-200", "medical-ai-200")) {
			rows.addAll(SensitivitySweep.run(load(n), List.of(10, 20), OptimizerConfig.DEFAULT));
		}
		String md = SensitivitySweep.markdown(rows);
		Path out = Path.of("target", "optimizer-sweep.md");
		Files.createDirectories(out.getParent());
		Files.writeString(out, md);
		assertThat(md).contains("## graph neural networks").contains("Intermediate/Balanced additive β=0.0")
				.contains("Intermediate/Balanced QW β=4.0").contains("Beginner/Recent (chosen)");
	}

}
