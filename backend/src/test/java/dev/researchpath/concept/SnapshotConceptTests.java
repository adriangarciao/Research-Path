package dev.researchpath.concept;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.researchpath.TestcontainersConfiguration;
import dev.researchpath.concept.ConceptModel.Representation;
import dev.researchpath.openalex.OpenAlexClient;
import dev.researchpath.openalex.OpenAlexSearchPage;
import dev.researchpath.snapshot.CandidateSnapshotService;
import dev.researchpath.snapshot.SnapshotNotEvaluatedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** M07: concept model built from a snapshot's frozen inputs (real PostgreSQL 17). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SnapshotConceptTests {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final String Q = "reinforcement learning in robotics";

	@Autowired
	CandidateSnapshotService snapshots;

	@Autowired
	ConceptService concepts;

	@Autowired
	JdbcClient jdbc;

	@MockitoBean
	OpenAlexClient openAlex;

	@BeforeEach
	void clean() {
		jdbc.sql("TRUNCATE candidate_snapshot, publication, concept RESTART IDENTITY CASCADE").update();
	}

	private static JsonNode work(String id, String title, Map<String, Double> keywords) {
		return work(id, title, keywords, "Reinforcement Learning in Robotics");
	}

	private static JsonNode work(String id, String title, Map<String, Double> keywords, String topic) {
		Map<String, Object> w = new LinkedHashMap<>();
		w.put("id", "https://openalex.org/" + id);
		w.put("display_name", title);
		List<Map<String, Object>> kws = new ArrayList<>();
		keywords.forEach((name, score) -> kws.add(Map.of("id", "https://openalex.org/keywords/" + name.replace(' ', '-'),
				"display_name", name, "score", score)));
		w.put("keywords", kws);
		w.put("topics", List.of(Map.of("id", "https://openalex.org/T" + Math.abs(topic.hashCode()), "display_name", topic,
				"score", 0.99)));
		return JSON.valueToTree(w);
	}

	private UUID snapshot(JsonNode... works) {
		when(openAlex.searchWorks(eq(Q), eq(1), anyInt(), any()))
				.thenReturn(new OpenAlexSearchPage((long) works.length, 1, List.of(works), Instant.parse("2026-10-17T12:00:00Z")));
		return snapshots.createSnapshot(Q, 50).id();
	}

	@Test
	void conceptsComeOnlyFromEligibleFrozenCandidatesWithoutProviderCalls() {
		UUID id = snapshot(
				work("W1", "Deep reinforcement learning for robot navigation", Map.of("robot navigation", 0.9, "reinforcement learning", 1.0)),
				work("W2", "Reinforcement learning for robot navigation in crowds", Map.of("robot navigation", 0.8)),
				work("W3", "Sim-to-real reinforcement learning for robots", Map.of("sim-to-real transfer", 0.9)),
				work("W4", "Sim-to-real transfer of robot reinforcement learning", Map.of("sim-to-real transfer", 0.7)),
				work("W5", "Ethics of reinforcement learning in robotics", Map.of("robot navigation", 0.9, "ai ethics", 0.9)), // tangential
				work("W6", "Protein folding", Map.of("robot navigation", 0.9), "Protein Structure Analysis")); // ineligible
		clearInvocations(openAlex);

		ConceptModel m = concepts.build(id, Representation.COMBINED).orElseThrow();

		verify(openAlex, never()).searchWorks(anyString(), anyInt(), anyInt(), any());
		assertThat(m.candidates()).isEqualTo(4); // W5 and W6 never contribute
		assertThat(m.included()).extracting(ConceptModel.Entry::label)
				.containsExactly("robot navigation", "sim-to-real transfer");
		assertThat(m.included().get(0).support()).isEqualTo(2); // W5/W6 support not counted
		assertThat(m.excludedByReason()).containsKey("QUERY_OR_GENERIC"); // shared topic + "reinforcement learning"
	}

	@Test
	void sameSnapshotGivesTheSameModelAfterACacheRefresh() {
		UUID id = snapshot(
				work("W1", "Reinforcement learning for robot navigation", Map.of("robot navigation", 0.9)),
				work("W2", "Robot reinforcement learning for navigation", Map.of("robot navigation", 0.8)));
		ConceptModel before = concepts.build(id, Representation.COMBINED).orElseThrow();

		// A later snapshot refreshes W1 in the cache with different keywords.
		when(openAlex.searchWorks(eq("robot learning"), eq(1), anyInt(), any())).thenReturn(new OpenAlexSearchPage(1L, 1,
				List.of(work("W1", "Reinforcement learning for robot navigation", Map.of("legged locomotion", 0.9))),
				Instant.parse("2026-10-18T12:00:00Z")));
		snapshots.createSnapshot("robot learning", 10);

		assertThat(concepts.build(id, Representation.COMBINED).orElseThrow()).isEqualTo(before);
	}

	@Test
	void unknownAndLegacySnapshotsAreReportedClearly() {
		assertThat(concepts.build(UUID.randomUUID(), Representation.COMBINED)).isEmpty();

		UUID legacy = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO candidate_snapshot (id, topic, normalized_topic, provider, retrieval_params, requested_count,
				    received_count, stored_count, rejected_count, normalizer_version, created_at)
				VALUES (:id, 'legacy', 'legacy', 'OPENALEX', '{}'::jsonb, 1, 0, 0, 0, 1, :at)
				""").param("id", legacy).param("at", Timestamp.from(Instant.parse("2026-10-17T12:00:00Z"))).update();
		assertThatThrownBy(() -> concepts.build(legacy, Representation.COMBINED))
				.isInstanceOf(SnapshotNotEvaluatedException.class);
	}

}
