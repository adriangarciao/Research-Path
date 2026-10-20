package dev.researchpath.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.researchpath.optimizer.GreedyOptimizer;
import dev.researchpath.optimizer.OptimizerConfig;
import dev.researchpath.optimizer.Preferences;

/** D16 regression: default paths keep a topic's landmark works (audit and fresh validation topics). */
class LandmarkRetentionTest {

	@ParameterizedTest
	@ValueSource(strings = { "gnn-200", "rl-robotics-200", "medical-ai-200", "nmt-200", "crispr-200", "urban-heat-200" })
	void defaultKeepsAtLeastTwoOfTheFiveMostCitedWorksAtTen(String name) throws Exception {
		BenchmarkDataset d = OptimizerBenchmarkTest.load(name);
		List<String> landmarks = Ablation.landmarks(d);
		GreedyOptimizer.Result r = new OptimizerSelector(Preferences.DEFAULT, OptimizerConfig.DEFAULT).optimize(d, 10);
		assertThat(r.selectedIds().stream().filter(landmarks::contains).count()).as(name).isGreaterThanOrEqualTo(2);
		// Seeds come first, carry the LANDMARK reason and are among the pool's most-cited works.
		r.decisions().stream().filter(dec -> dec.reason() == GreedyOptimizer.Reason.LANDMARK)
				.forEach(dec -> assertThat(landmarks).contains(dec.id()));
		assertThat(r.decisions().stream().filter(dec -> dec.reason() == GreedyOptimizer.Reason.LANDMARK).count())
				.isBetween(1L, 2L);
	}

	@Test
	void proteinStructurePathNowIncludesAlphaFold2() throws Exception {
		BenchmarkDataset d = OptimizerBenchmarkTest.load("protein-structure-200");
		assertThat(ReadingPaths.path(d, 10, Preferences.DEFAULT).orderedIds()).contains("W3177828909");
	}

	@Test
	void fivePaperPathsReserveAtMostOneSlot() throws Exception {
		BenchmarkDataset d = OptimizerBenchmarkTest.load("gnn-200");
		GreedyOptimizer.Result r = new OptimizerSelector(Preferences.DEFAULT, OptimizerConfig.DEFAULT).optimize(d, 5);
		assertThat(r.decisions().stream().filter(dec -> dec.reason() == GreedyOptimizer.Reason.LANDMARK).count()).isEqualTo(1);
	}

}
