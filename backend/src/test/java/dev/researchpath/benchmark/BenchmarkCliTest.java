package dev.researchpath.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkCliTest {

	private static final List<Path> DATASETS = List.of(RealDatasetBenchmarkTest.DIR.resolve("gnn-200.json"),
			RealDatasetBenchmarkTest.DIR.resolve("medical-ai-200.json"));

	@Test
	void writesMarkdownAndJsonFromDatasetFilesOnly(@TempDir Path out) throws Exception {
		BenchmarkCli.main(new String[] { "--out", out.toString(), DATASETS.get(0).toString(), DATASETS.get(1).toString() });

		String md = Files.readString(out.resolve("baseline-report.md"));
		assertThat(md).contains("## graph neural networks").contains("## AI-assisted medical diagnosis")
				.contains(RealDatasetBenchmarkTest.DATASET_SHA256.get("gnn-200"))
				.contains("| RELEVANCE | 10 |").contains("| CITATION | 20 |").contains("| PROVIDER_ORDER | 5 |");
		String json = Files.readString(out.resolve("baseline-runs.json"));
		assertThat(json).contains("\"datasetSha256\"").contains("\"selectedIds\"").doesNotContain("\r\n");
	}

	@Test
	void reportIsReproducibleApartFromTimingColumns() throws Exception {
		Clock fixed = Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC);
		String a = withoutTimings(BenchmarkCli.markdown(BenchmarkCli.run(DATASETS, fixed)));
		String b = withoutTimings(BenchmarkCli.markdown(BenchmarkCli.run(DATASETS, fixed)));
		assertThat(a).isEqualTo(b);
	}

	private static String withoutTimings(String md) {
		return md.replaceAll("\\| \\d+ µs \\|", "| _ |");
	}

}
