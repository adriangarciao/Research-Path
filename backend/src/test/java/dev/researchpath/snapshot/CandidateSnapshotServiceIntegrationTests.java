package dev.researchpath.snapshot;

import static dev.researchpath.snapshot.WorkJson.page;
import static dev.researchpath.snapshot.WorkJson.work;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import dev.researchpath.TestcontainersConfiguration;
import dev.researchpath.openalex.OpenAlexClient;
import dev.researchpath.openalex.OpenAlexException;
import dev.researchpath.openalex.OpenAlexSearchPage;
import dev.researchpath.publication.NormalizedWork;
import dev.researchpath.publication.PublicationRepository;
import tools.jackson.databind.JsonNode;

/** Real PostgreSQL 17 (Testcontainers); only the HTTP client to OpenAlex is mocked. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CandidateSnapshotServiceIntegrationTests {

	@Autowired
	CandidateSnapshotService service;

	@Autowired
	JdbcClient jdbc;

	@MockitoBean
	OpenAlexClient openAlex;

	@MockitoSpyBean
	PublicationRepository publications;

	@BeforeEach
	void cleanDatabase() {
		jdbc.sql("TRUNCATE candidate_snapshot, publication, concept RESTART IDENTITY CASCADE").update();
	}

	private OpenAlexSearchPage realFixturePage() throws Exception {
		try (InputStream in = getClass().getResourceAsStream("/openalex/search-graph-neural-networks-page1.json")) {
			JsonNode body = WorkJson.JSON.readTree(in);
			List<JsonNode> results = new ArrayList<>();
			body.path("results").forEach(results::add);
			return new OpenAlexSearchPage(body.path("meta").path("count").longValue(), 1, results,
					Instant.parse("2026-10-17T20:30:00Z"));
		}
	}

	@Test
	void realFixtureProducesAuditStyleSummaryAndFullPersistence() throws Exception {
		when(openAlex.searchWorks("graph neural networks", 1, 3)).thenReturn(realFixturePage());

		SnapshotSummary s = service.createSnapshot("  graph   neural networks ", 3);

		assertThat(s.topic()).isEqualTo("graph neural networks");
		assertThat(s.provider()).isEqualTo("OPENALEX");
		assertThat(s.providerTotalCount()).isEqualTo(964424L);
		assertThat(s.requestedCount()).isEqualTo(3);
		assertThat(s.receivedCount()).isEqualTo(3);
		assertThat(s.storedCount()).isEqualTo(3);
		assertThat(s.rejectedCount()).isZero();
		assertThat(s.normalizerVersion()).isEqualTo(1);
		// Expected numbers computed independently from the fixture with Python.
		assertThat(s.availability()).isEqualTo(new SnapshotSummary.Availability(2, 3, 3, 3, 3, 3));
		assertThat(s.classification()).isEqualTo(
				new SnapshotSummary.Classification(4, 19, "Advanced Graph Neural Networks", 3));
		assertThat(s.internalCitationEdges()).isEqualTo(3);
		assertThat(s.possibleDuplicatePairs()).isZero();

		// Children persisted with confidence scores and actual reference ids.
		assertThat(count("SELECT count(*) FROM publication_author pa JOIN publication p ON p.id = pa.publication_id "
				+ "WHERE p.openalex_id = 'W4392553889'")).isEqualTo(5);
		assertThat(count("SELECT count(*) FROM publication_concept WHERE score IS NULL")).isZero();
		assertThat(count("SELECT count(*) FROM publication_reference")).isEqualTo(76 + 111 + 99);

		// Membership freezes the exact provider record, rank and relevance; nothing is screened yet.
		assertThat(jdbc.sql("""
				SELECT source_record->>'id' FROM snapshot_membership WHERE snapshot_id = :id AND provider_rank = 1
				""").param("id", s.id()).query(String.class).single()).isEqualTo("https://openalex.org/W4392553889");
		assertThat(jdbc.sql("SELECT provider_relevance_score FROM snapshot_membership WHERE provider_rank = 3")
				.query(Double.class).single()).isEqualTo(235.25731);
		assertThat(count("SELECT count(*) FROM snapshot_membership WHERE eligibility_status <> 'UNSCREENED'")).isZero();

		String params = jdbc.sql("SELECT retrieval_params::text FROM candidate_snapshot").query(String.class).single();
		assertThat(params).contains("\"search\": \"graph neural networks\"").contains("\"pages\": [1]");
	}

	@Test
	void laterSnapshotRefreshesCacheButEarlierSnapshotKeepsItsRecord() {
		when(openAlex.searchWorks(eq("topic a"), eq(1), anyInt()))
				.thenReturn(page(1, 1, work("W100", "Old title").year(2019)));
		when(openAlex.searchWorks(eq("topic b"), eq(1), anyInt()))
				.thenReturn(page(1, 2, work("W100", "New title").year(2019), work("W200", "Other")));

		SnapshotSummary first = service.createSnapshot("topic a", 5);
		SnapshotSummary second = service.createSnapshot("topic b", 5);

		assertThat(count("SELECT count(*) FROM publication WHERE openalex_id = 'W100'")).isEqualTo(1);
		assertThat(jdbc.sql("SELECT title FROM publication WHERE openalex_id = 'W100'").query(String.class).single())
				.isEqualTo("New title");
		assertThat(sourceTitle(first.id(), 1)).isEqualTo("Old title");
		assertThat(sourceTitle(second.id(), 1)).isEqualTo("New title");
		assertThat(first.storedCount()).isEqualTo(1);
		assertThat(second.storedCount()).isEqualTo(2);
	}

	@Test
	void possibleDuplicatesAreRecordedNotMerged() {
		when(openAlex.searchWorks(anyString(), eq(1), anyInt())).thenReturn(page(1, 4,
				work("W1", "A Comprehensive Survey on Graph Neural Networks").year(2020).doi("10.1/survey"),
				work("W2", "A comprehensive survey on graph neural networks.").year(2020),
				work("W3", "Preprint version").doi("10.9/SAME"),
				work("W4", "Journal version").doi("10.9/same")));

		SnapshotSummary s = service.createSnapshot("gnn survey", 10);

		assertThat(s.storedCount()).isEqualTo(4); // nothing merged or dropped
		assertThat(s.possibleDuplicatePairs()).isEqualTo(2);
		assertThat(jdbc.sql("""
				SELECT match_type || ':' || (evidence->'a'->>'openalex_id') || '-' || (evidence->'b'->>'openalex_id')
				    || ':' || review_status
				FROM publication_identity_candidate ORDER BY match_type
				""").query(String.class).list())
				.containsExactly("SAME_DOI:W3-W4:UNREVIEWED", "SAME_NORMALIZED_TITLE:W1-W2:UNREVIEWED");
	}

	@Test
	void paginatesAndRecordsRejectionsAndPageDrift() {
		// max-per-page is 100, so 150 requested => pages of 100: page 1 full, page 2 short (end of results).
		List<WorkJson> first = new ArrayList<>();
		for (int i = 1; i <= 100; i++) {
			first.add(work("W" + i, "Work " + i));
		}
		when(openAlex.searchWorks("big topic", 1, 100)).thenReturn(page(1, 103, first.toArray(WorkJson[]::new)));
		when(openAlex.searchWorks("big topic", 2, 100)).thenReturn(page(2, 103,
				work("W100", "Work 100"), // same work again: ranking drifted between pages
				work("W101", null), // unusable: no title
				work("W102", "Work 102")));

		SnapshotSummary s = service.createSnapshot("big topic", 150);

		verify(openAlex).searchWorks("big topic", 1, 100);
		verify(openAlex).searchWorks("big topic", 2, 100);
		verify(openAlex, never()).searchWorks("big topic", 3, 100);
		assertThat(s.receivedCount()).isEqualTo(103);
		assertThat(s.storedCount()).isEqualTo(101);
		assertThat(s.rejectedCount()).isEqualTo(1);
		assertThat(s.retrievalNotes()).hasSize(2);
		assertThat(s.retrievalNotes().toString())
				.contains("\"DUPLICATE_IN_RESPONSE\"").contains("\"provider_rank\":101")
				.contains("\"REJECTED\"").contains("\"MISSING_TITLE\"").contains("\"provider_rank\":102");
		assertThat(jdbc.sql("SELECT provider_rank FROM snapshot_membership WHERE publication_id = "
				+ "(SELECT id FROM publication WHERE openalex_id = 'W102')").query(Integer.class).single())
				.isEqualTo(103);
	}

	@Test
	void stopsAtRequestedCount() {
		when(openAlex.searchWorks("t", 1, 2)).thenReturn(page(1, 999, work("W1", "a"), work("W2", "b")));

		SnapshotSummary s = service.createSnapshot("t", 2);

		assertThat(s.receivedCount()).isEqualTo(2);
		verify(openAlex, never()).searchWorks("t", 2, 2);
	}

	@Test
	void providerFailureMidRetrievalPersistsNothing() {
		List<WorkJson> full = new ArrayList<>();
		for (int i = 1; i <= 100; i++) {
			full.add(work("W" + i, "Work " + i));
		}
		when(openAlex.searchWorks("t", 1, 100)).thenReturn(page(1, 500, full.toArray(WorkJson[]::new)));
		when(openAlex.searchWorks("t", 2, 100)).thenThrow(new OpenAlexException("HTTP 429", 429, null));

		assertThatThrownBy(() -> service.createSnapshot("t", 200)).isInstanceOf(OpenAlexException.class);

		assertThat(count("SELECT count(*) FROM candidate_snapshot")).isZero();
		assertThat(count("SELECT count(*) FROM publication")).isZero();
	}

	@Test
	void databaseFailureMidPersistRollsBackEverything() {
		when(openAlex.searchWorks(anyString(), eq(1), anyInt()))
				.thenReturn(page(1, 3, work("W1", "a"), work("W2", "b"), work("W3", "c")));
		// First two upserts succeed, the third fails inside the transaction.
		doCallRealMethod().doCallRealMethod().doThrow(new IllegalStateException("simulated DB failure"))
				.when(publications).upsert(any(NormalizedWork.class), any(Instant.class));

		assertThatThrownBy(() -> service.createSnapshot("t", 3)).hasMessageContaining("simulated DB failure");

		assertThat(count("SELECT count(*) FROM candidate_snapshot")).isZero();
		assertThat(count("SELECT count(*) FROM publication")).isZero();
		assertThat(count("SELECT count(*) FROM snapshot_membership")).isZero();
	}

	@Test
	void validatesInput() {
		assertThatThrownBy(() -> service.createSnapshot("   ", 10)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.createSnapshot("x".repeat(301), 10))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.createSnapshot("t", 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.createSnapshot("t", 201)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void defaultCandidateCountIsConfigured() {
		when(openAlex.searchWorks("t", 1, 100)).thenReturn(page(1, 1, work("W1", "a")));
		assertThat(service.createSnapshot("t", null).requestedCount()).isEqualTo(100);
	}

	@Test
	void unknownSnapshotIsEmpty() {
		assertThat(service.findSummary(UUID.randomUUID())).isEmpty();
	}

	private String sourceTitle(UUID snapshotId, int rank) {
		return jdbc.sql("""
				SELECT source_record->>'display_name' FROM snapshot_membership
				WHERE snapshot_id = :id AND provider_rank = :rank
				""").param("id", snapshotId).param("rank", rank).query(String.class).single();
	}

	private long count(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

}
