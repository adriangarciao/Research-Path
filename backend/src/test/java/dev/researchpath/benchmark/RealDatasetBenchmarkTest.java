package dev.researchpath.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * M08 gate on the committed datasets: integrity (SHA-256 as recorded in data/benchmarks/README.md),
 * determinism, budgets, uniqueness, and pinned result fingerprints so baseline outputs on these fixed
 * inputs cannot change silently. Offline: reads files only.
 */
class RealDatasetBenchmarkTest {

	static final Path DIR = Path.of("..", "data", "benchmarks");
	static final Clock FIXED = Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC);

	static final Map<String, String> DATASET_SHA256 = Map.of(
			"gnn-200", "cb67e8d22bb3657ad7feb2683683d7abdf11931f68b68390ea5429adb805a710",
			"rl-robotics-200", "52b61eb823a178896169f1c780c983246f3fe7321422cf9d75ff17d2f2d3a306",
			"medical-ai-200", "ba7725f92857eaf158ea8c15b426c99e6f6123293382b8a8097515fc0104ad49");

	/** Fingerprint of all selected ids and metrics (runtime excluded) per dataset; update only deliberately. */
	static final Map<String, String> RESULT_FINGERPRINTS = Map.of(
			"gnn-200", "2eac9dc2b96cac62acd008d23acfe300634b65ee36a13fe8c750c59880c80fc6",
			"rl-robotics-200", "3124b0c674f455ed157ab184f06e044d1ed98ff329cd041411549a2ffc42d267",
			"medical-ai-200", "7924ec8697c09889ca2eb10305618889bd54609d458fc362682435c19b7d2240");

	static BenchmarkRunner.Run run(String name) throws Exception {
		Path file = DIR.resolve(name + ".json");
		return BenchmarkRunner.run(DatasetIo.read(file), name + ".json", DatasetIo.sha256(file), Baselines.ALL,
				BenchmarkRunner.BUDGETS, FIXED);
	}

	static String fingerprint(BenchmarkRunner.Run run) throws Exception {
		StringBuilder sb = new StringBuilder();
		for (BenchmarkRunner.Result r : run.results()) {
			sb.append(r.selector()).append('|').append(r.budget()).append('|').append(r.selectedIds()).append('|')
					// Pretty-printed JSON uses the platform line separator; normalize so Windows and Linux agree (CI).
					.append(DatasetIo.JSON.writeValueAsString(r.metrics()).replace("\r\n", "\n")).append('\n');
		}
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "gnn-200", "rl-robotics-200", "medical-ai-200" })
	void datasetIsTheRecordedFrozenVersion(String name) throws Exception {
		assertThat(DatasetIo.sha256(DIR.resolve(name + ".json"))).isEqualTo(DATASET_SHA256.get(name));
	}

	@ParameterizedTest
	@ValueSource(strings = { "gnn-200", "rl-robotics-200", "medical-ai-200" })
	void baselinesAreDeterministicBoundedAndUnique(String name) throws Exception {
		BenchmarkRunner.Run a = run(name);
		BenchmarkRunner.Run b = run(name);
		assertThat(a.results()).hasSize(Baselines.ALL.size() * BenchmarkRunner.BUDGETS.size());
		for (int i = 0; i < a.results().size(); i++) {
			BenchmarkRunner.Result x = a.results().get(i);
			BenchmarkRunner.Result y = b.results().get(i);
			assertThat(x.selectedIds()).isEqualTo(y.selectedIds());
			assertThat(x.metrics()).isEqualTo(y.metrics());
			assertThat(x.selectedIds()).hasSizeLessThanOrEqualTo(x.budget()).doesNotHaveDuplicates();
			assertThat(x.metrics().duplicateDoiPairs()).isZero();
			assertThat(x.metrics().relevanceMin()).isGreaterThanOrEqualTo(0.75); // gate floor holds
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "gnn-200", "rl-robotics-200", "medical-ai-200" })
	void resultsMatchThePinnedFingerprint(String name) throws Exception {
		BenchmarkRunner.Run run = run(name);
		String actual = fingerprint(run);
		if (!actual.equals(RESULT_FINGERPRINTS.get(name))) {
			Path out = Path.of("target", "benchmark-fingerprint-" + name + ".json");
			Files.createDirectories(out.getParent());
			Files.writeString(out, DatasetIo.JSON.writeValueAsString(run.results()));
		}
		assertThat(actual).as("results changed for %s; see target/benchmark-fingerprint-%s.json", name, name)
				.isEqualTo(RESULT_FINGERPRINTS.get(name));
	}

}
