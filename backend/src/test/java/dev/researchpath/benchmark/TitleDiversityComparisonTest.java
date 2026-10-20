package dev.researchpath.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.researchpath.optimizer.TitleKey;

/** D18 on real frozen pools: the constraint holds and the motivating example is resolved. */
class TitleDiversityComparisonTest {

	private static BenchmarkDataset dataset(String name) throws Exception {
		return DatasetIo.read(RealDatasetBenchmarkTest.DIR.resolve(name + ".json"));
	}

	@Test
	void quantumErrorCorrectionKeepsOneWorkTitledExactlyLikeTheTopic() throws Exception {
		List<TitleDiversityComparison.Row> rows = TitleDiversityComparison.run(dataset("quantum-ec-200"));
		String topicKey = TitleKey.of("Quantum Error Correction");
		for (TitleDiversityComparison.Row r : rows) {
			assertThat(r.after().titleRepeats()).as("k=%d", r.budget()).isZero();
			assertThat(r.after().size()).isEqualTo(r.budget());
			assertThat(r.after().titles().stream().filter(t -> topicKey.equals(TitleKey.of(t)))).hasSize(1);
		}
		TitleDiversityComparison.Row k10 = rows.stream().filter(r -> r.budget() == 10).findFirst().orElseThrow();
		assertThat(k10.before().titles().stream().filter(t -> topicKey.equals(TitleKey.of(t))).count())
				.as("the unchanged default repeats the topic title").isGreaterThanOrEqualTo(2);
	}

	@Test
	void everyCheckIsComputedAndTheConstraintAlwaysHolds() throws Exception {
		List<TitleDiversityComparison.Row> rows = TitleDiversityComparison.run(dataset("differential-privacy-200"));
		List<TitleDiversityComparison.Check> checks = TitleDiversityComparison.checks(rows);
		assertThat(checks).extracting(TitleDiversityComparison.Check::id)
				.containsExactly("C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9");
		assertThat(checks.get(0).passed()).as("C1").isTrue();
		assertThat(checks.get(6).passed()).as("C7").isTrue();
		assertThat(rows).allSatisfy(r -> assertThat(r.after().titleRepeats()).isZero());
	}

}
