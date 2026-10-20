package dev.researchpath.benchmark;

import static dev.researchpath.snapshot.WorkJsonAccess.page;
import static dev.researchpath.snapshot.WorkJsonAccess.work;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.researchpath.TestcontainersConfiguration;
import dev.researchpath.openalex.OpenAlexClient;
import dev.researchpath.snapshot.CandidateSnapshotService;

/** M08: datasets exported from frozen inputs contain exactly the optimization candidates. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DatasetExportTests {

	private static final String Q = "reinforcement learning in robotics";

	@Autowired
	CandidateSnapshotService snapshots;

	@Autowired
	DatasetExportService exports;

	@Autowired
	JdbcClient jdbc;

	@MockitoBean
	OpenAlexClient openAlex;

	@BeforeEach
	void clean() {
		jdbc.sql("TRUNCATE candidate_snapshot, publication, concept RESTART IDENTITY CASCADE").update();
	}

	@Test
	void exportContainsOnlyEligibleCanonicalCandidatesWithTheirFrozenFields() {
		when(openAlex.searchWorks(eq(Q), eq(1), anyInt(), any())).thenReturn(page(1, 4,
				work("W1", "Reinforcement learning in robotics: a survey").year(2013).doi("10.1/a").author("A1", "Jens Kober").cites("W9"),
				work("W2", "Reinforcement learning in robotics: a survey").year(2013).author("A1", "Jens Kober"), // duplicate
				work("W3", "Ethics of reinforcement learning in robotics"), // tangential
				work("W4", "Deep reinforcement learning for robot navigation").year(2020)));
		UUID id = snapshots.createSnapshot(Q, 10).id();
		clearInvocations(openAlex);

		BenchmarkDataset d = exports.export(id).orElseThrow();

		verify(openAlex, never()).searchWorks(anyString(), anyInt(), anyInt(), any());
		assertThat(d.formatVersion()).isEqualTo(BenchmarkDataset.FORMAT_VERSION);
		assertThat(d.topic()).isEqualTo(Q);
		assertThat(d.policies().identityPolicyVersion()).isEqualTo(1);
		assertThat(d.candidates()).extracting(BenchmarkDataset.Candidate::openalexId).containsExactly("W1", "W4");
		BenchmarkDataset.Candidate w1 = d.candidates().get(0);
		assertThat(w1.providerRank()).isEqualTo(1);
		assertThat(w1.sourceLink()).isEqualTo("https://doi.org/10.1/a");
		assertThat(w1.sourceLinkKind()).isEqualTo("DOI");
		assertThat(w1.relevanceCoverage()).isEqualTo(1.0);
		assertThat(w1.referencedWorkIds()).containsExactly("W9");
		assertThat(w1.citedByCount()).isNull(); // unknown stays unknown
		assertThat(DatasetIo.toJson(d)).doesNotContain("abstract").doesNotContain("Bearer");
	}

	@Test
	void unknownSnapshotExportsNothing() {
		assertThat(exports.export(UUID.randomUUID())).isEmpty();
	}

}
