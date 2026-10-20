package dev.researchpath.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** D16: the sweep's acceptance rule is the pre-registered one and the sweep is reproducible. */
class LandmarkSweepTest {

	private static final LandmarkSweep.Setting S = new LandmarkSweep.Setting(4, 0.5, 0);

	private static LandmarkSweep.Row row(String topic, int k, double loss, int landmarks, boolean d12) {
		return new LandmarkSweep.Row(topic, S, k, 0.8, loss, landmarks, 1.0, 0.99, 100.0, d12);
	}

	private static List<LandmarkSweep.Row> setting(int[] landmarks, double loss, boolean d12) {
		List<LandmarkSweep.Row> rows = new ArrayList<>();
		int i = 0;
		for (String t : LandmarkSweep.SELECTION_TOPICS.stream().sorted().toList()) {
			rows.add(row(t, 10, loss, landmarks[i++], d12));
			rows.add(row(t, 20, loss, 5, d12));
		}
		rows.add(row("a validation topic", 10, 0.5, 0, false)); // never used to choose
		return rows;
	}

	@Test
	void acceptanceFollowsThePreRegisteredCriteria() {
		assertThat(LandmarkSweep.accepted(setting(new int[] { 2, 2, 2 }, 0.05, true), S)).isTrue();
		assertThat(LandmarkSweep.accepted(setting(new int[] { 1, 2, 3 }, 0.01, true), S)).as("mean exactly 2").isTrue();
		assertThat(LandmarkSweep.accepted(setting(new int[] { 0, 3, 3 }, 0.01, true), S)).as("a topic with 0").isFalse();
		assertThat(LandmarkSweep.accepted(setting(new int[] { 1, 1, 2 }, 0.01, true), S)).as("mean below 2").isFalse();
		assertThat(LandmarkSweep.accepted(setting(new int[] { 3, 3, 3 }, 0.051, true), S)).as("coverage loss").isFalse();
		assertThat(LandmarkSweep.accepted(setting(new int[] { 3, 3, 3 }, 0.01, false), S)).as("D12 broken").isFalse();
		assertThat(LandmarkSweep.accepted(setting(new int[] { 3, 3, 3 }, 0.01, true), new LandmarkSweep.Setting(6, 0.5, 0)))
				.as("other setting").isFalse();
	}

	@Test
	void sweepIsReproducibleAndTheCurrentDefaultHasNoLoss() throws Exception {
		BenchmarkDataset d = OptimizerBenchmarkTest.load("gnn-200");
		List<LandmarkSweep.Row> a = LandmarkSweep.run(d, List.of(10));
		assertThat(a).isEqualTo(LandmarkSweep.run(d, List.of(10)));
		LandmarkSweep.Row reference = a.stream().filter(r -> r.setting().equals(new LandmarkSweep.Setting(4, 0, 0)))
				.findFirst().orElseThrow();
		assertThat(reference.coverageLoss()).isZero();
		assertThat(LandmarkSweep.markdown(a)).contains("Pre-registered acceptance", "(selection)");
	}

}
