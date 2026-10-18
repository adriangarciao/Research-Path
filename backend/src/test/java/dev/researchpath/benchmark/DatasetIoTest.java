package dev.researchpath.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.researchpath.publication.NormalizedWork.ConceptAssociation;
import dev.researchpath.publication.NormalizedWork.ConceptKind;

class DatasetIoTest {

	static BenchmarkDataset sample() {
		return new BenchmarkDataset(BenchmarkDataset.FORMAT_VERSION, UUID.fromString("7e49ed44-2332-4220-bc97-f6f4ebc1e66a"),
				"reinforcement learning in robotics", Instant.parse("2026-10-17T21:40:00Z"),
				new BenchmarkDataset.Policies(2, 1, 1),
				List.of(new BenchmarkDataset.Candidate("W1", 1, "Robot RL", 2020, "article", "10.1/x", 12, 1.5, 0.9,
						"https://doi.org/10.1/x", "DOI", 1.0, 1.0,
						List.of(new ConceptAssociation(ConceptKind.KEYWORD, "keywords/robot-navigation", "robot navigation",
								0.9, false, null, null, null)),
						List.of("W9")),
						new BenchmarkDataset.Candidate("W2", 2, "Unknown citations", null, null, null, null, null, null,
								null, null, 0.75, 0.5, List.of(), List.of())));
	}

	@Test
	void roundTripsExactly(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("d.json");
		DatasetIo.write(sample(), file);
		assertThat(DatasetIo.read(file)).isEqualTo(sample());
		String json = Files.readString(file);
		assertThat(json).doesNotContain("\r\n").doesNotContain("abstract").contains("\"citedByCount\" : null");
	}

	@Test
	void hashIgnoresLineEndings(@TempDir Path dir) throws Exception {
		String json = DatasetIo.toJson(sample());
		Path lf = dir.resolve("lf.json");
		Path crlf = dir.resolve("crlf.json");
		Files.writeString(lf, json, StandardCharsets.UTF_8);
		Files.writeString(crlf, json.replace("\n", "\r\n"), StandardCharsets.UTF_8);
		assertThat(DatasetIo.sha256(lf)).isEqualTo(DatasetIo.sha256(crlf)).hasSize(64);
	}

	@Test
	void rejectsUnsupportedFormatVersion() {
		String json = DatasetIo.toJson(sample()).replace("\"formatVersion\" : 1", "\"formatVersion\" : 99");
		assertThatThrownBy(() -> DatasetIo.fromJson(json)).hasMessageContaining("format version 99");
	}

}
