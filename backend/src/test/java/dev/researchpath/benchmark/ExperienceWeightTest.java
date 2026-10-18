package dev.researchpath.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.researchpath.optimizer.OptimizerConfig;

/** The D09 criteria, enforced on the committed datasets for the default experience weight. */
class ExperienceWeightTest {

	private static final List<String> DATASETS = List.of("gnn-200", "rl-robotics-200", "medical-ai-200");

	@Test
	void defaultWeightNudgesSelectionsWithoutReplacingThem() throws Exception {
		int topicsChanged = 0;
		for (String name : DATASETS) {
			BenchmarkDataset d = OptimizerBenchmarkTest.load(name);
			List<ExperienceSweep.Row> rows = ExperienceSweep.run(d, List.of(10, 20), OptimizerConfig.DEFAULT).stream()
					.filter(r -> r.weight() == OptimizerConfig.DEFAULT.experienceWeight()).toList();
			assertThat(rows).hasSize(2);
			for (ExperienceSweep.Row r : rows) {
				assertThat(r.ordered()).as("%s k=%d overview order B >= I >= A: %s", name, r.budget(), r).isTrue();
				assertThat(r.nudgesOnly()).as("%s k=%d shares >= 60%% with Intermediate: %s", name, r.budget(), r).isTrue();
				assertThat(r.advancedKeepsOverview()).as("%s k=%d Advanced keeps an overview: %s", name, r.budget(), r).isTrue();
			}
			if (rows.stream().anyMatch(ExperienceSweep.Row::changesSomething)) {
				topicsChanged++;
			}
		}
		assertThat(topicsChanged).as("experience level must visibly change something").isGreaterThanOrEqualTo(2);
	}

	@Test
	void theOldWeightFailsTheCriteria() throws Exception {
		// Regression evidence for the D09 change: 0.15 turned Beginner paths into mostly overview-marked papers.
		List<ExperienceSweep.Row> rows = new ArrayList<>();
		for (String name : DATASETS) {
			rows.addAll(ExperienceSweep.run(OptimizerBenchmarkTest.load(name), List.of(10), OptimizerConfig.DEFAULT));
		}
		assertThat(rows.stream().filter(r -> r.weight() == 0.15)).noneMatch(ExperienceSweep.Row::meetsCriteria);
	}

	@Test
	void sweepReportIsDeterministicAndMarksTheChosenWeight() throws Exception {
		List<ExperienceSweep.Row> rows = ExperienceSweep.run(OptimizerBenchmarkTest.load("gnn-200"), List.of(10),
				OptimizerConfig.DEFAULT);
		String md = ExperienceSweep.markdown(rows, OptimizerConfig.DEFAULT.experienceWeight());
		assertThat(md).contains("| **0.02** | 10 |").isEqualTo(ExperienceSweep.markdown(
				ExperienceSweep.run(OptimizerBenchmarkTest.load("gnn-200"), List.of(10), OptimizerConfig.DEFAULT), 0.02));
	}

}
