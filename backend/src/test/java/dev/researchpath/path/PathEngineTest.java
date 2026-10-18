package dev.researchpath.path;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.researchpath.benchmark.BenchmarkDataset;
import dev.researchpath.benchmark.DatasetIo;
import dev.researchpath.benchmark.ReadingPaths;
import dev.researchpath.optimizer.Preferences.Experience;
import dev.researchpath.optimizer.Preferences.Focus;

/** The product engine and the offline benchmark produce the same path from the same frozen pool. */
class PathEngineTest {

	@ParameterizedTest
	@ValueSource(strings = { "gnn-200", "rl-robotics-200", "medical-ai-200" })
	void productPathsEqualBenchmarkPaths(String name) throws Exception {
		BenchmarkDataset d = DatasetIo.read(Path.of("..", "data", "benchmarks", name + ".json"));
		for (Experience e : Experience.values()) {
			for (Focus f : Focus.values()) {
				for (int k : PathOptions.BUDGETS) {
					PathOptions o = new PathOptions(e, f, k);
					PathEngine.Generated g = PathEngine.run(d, o, Map.of());
					assertThat(g.path()).as("%s %s", name, o).isEqualTo(ReadingPaths.path(d, k, o.preferences()));
					assertThat(g.selection().selectedIds()).hasSize(Math.min(k, d.candidates().size()));
				}
			}
		}
	}

}
