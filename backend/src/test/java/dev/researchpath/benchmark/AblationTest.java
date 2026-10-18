package dev.researchpath.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.researchpath.benchmark.BenchmarkDataset.Candidate;
import dev.researchpath.optimizer.OptimizerConfig;
import dev.researchpath.optimizer.Preferences;

/** M14: the evaluation runner is deterministic, consistent and leak-free on frozen inputs. */
class AblationTest {

	@ParameterizedTest
	@ValueSource(strings = { "gnn-200", "causal-economics-200", "climate-agriculture-200" })
	void ablationIsDeterministicConsistentAndLeakFree(String name) throws Exception {
		BenchmarkDataset d = OptimizerBenchmarkTest.load(name);
		List<Ablation.Row> a = Ablation.run(d, List.of(10, 20));
		List<Ablation.Row> b = Ablation.run(d, List.of(10, 20));
		assertThat(a).hasSize(b.size());
		Set<String> pool = new HashSet<>();
		Map<String, Candidate> byId = new HashMap<>();
		d.candidates().forEach(c -> {
			pool.add(c.openalexId());
			byId.put(c.openalexId(), c);
		});
		var reference = SelectionMetrics.conceptModel(d);
		for (int i = 0; i < a.size(); i++) {
			Ablation.Row r = a.get(i);
			assertThat(r.selectedIds()).as("%s deterministic", r.variant()).isEqualTo(b.get(i).selectedIds());
			assertThat(pool).as("no cross-snapshot leakage").containsAll(r.selectedIds());
			assertThat(r.selectedIds()).doesNotHaveDuplicates().hasSize(Math.min(r.budget(), d.candidates().size()));
			// Every reported metric recomputes from the recorded selection with the shared denominator.
			SelectionMetrics again = SelectionMetrics.of(d, reference, r.budget(), r.selectedIds().stream().map(byId::get).toList());
			assertThat(r.metrics()).isEqualTo(again);
			assertThat(r.metrics().conceptUniverseSize()).isEqualTo(reference.included().size());
		}
		for (int k : List.of(10, 20)) {
			List<String> product = new OptimizerSelector(Preferences.DEFAULT, OptimizerConfig.DEFAULT).optimize(d, k).selectedIds();
			assertThat(a.stream().filter(r -> r.variant().equals("DEFAULT") && r.budget() == k).findFirst().orElseThrow()
					.selectedIds()).as("default variant is the product optimizer").isEqualTo(product);
		}
	}

}
