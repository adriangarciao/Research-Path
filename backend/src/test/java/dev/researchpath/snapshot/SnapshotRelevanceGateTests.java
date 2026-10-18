package dev.researchpath.snapshot;

import static dev.researchpath.snapshot.WorkJson.page;
import static dev.researchpath.snapshot.WorkJson.work;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.researchpath.TestcontainersConfiguration;
import dev.researchpath.openalex.OpenAlexClient;
import dev.researchpath.publication.NormalizationResult.Accepted;
import dev.researchpath.publication.OpenAlexWorkNormalizer;

/** M06: relevance gate and frozen evaluation inputs at snapshot creation (real PostgreSQL 17). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SnapshotRelevanceGateTests {

	private static final String Q = "reinforcement learning in robotics";

	@Autowired
	CandidateSnapshotService service;

	@Autowired
	JdbcClient jdbc;

	@MockitoBean
	OpenAlexClient openAlex;

	@BeforeEach
	void clean() {
		jdbc.sql("TRUNCATE candidate_snapshot, publication, concept RESTART IDENTITY CASCADE").update();
	}

	private SnapshotSummary snapshot(String topic, WorkJson... works) {
		when(openAlex.searchWorks(eq(topic), eq(1), anyInt())).thenReturn(page(1, works.length, works));
		return service.createSnapshot(topic, 50);
	}

	private List<String> tiers(SnapshotSummary s) {
		return jdbc.sql("""
				SELECT p.openalex_id || ':' || m.relevance_tier || ':' || m.eligibility_status FROM snapshot_membership m
				JOIN publication p ON p.id = m.publication_id WHERE m.snapshot_id = :id ORDER BY m.provider_rank
				""").param("id", s.id()).query(String.class).list();
	}

	@Test
	void gateAdmitsRelevantWorksAndExcludesOffTopicAndTangentialOnes() {
		SnapshotSummary s = snapshot(Q,
				work("W1", "Off-Policy Reinforcement Learning for Robotics"), // no abstract, technical "policy"
				work("W2", "Ethical considerations of reinforcement learning in robotics"), // unrequested aspect
				work("W3", "Protein structure prediction"), // off-topic
				work("W4", "Learning to grasp")); // partial evidence only

		assertThat(tiers(s)).containsExactly("W1:ELIGIBLE:ELIGIBLE", "W2:TANGENTIAL:INELIGIBLE",
				"W3:INELIGIBLE:INELIGIBLE", "W4:INELIGIBLE:INELIGIBLE");
		assertThat(s.relevance().policyVersion()).isEqualTo(1);
		assertThat(s.relevance().eligible()).isEqualTo(1);
		assertThat(s.relevance().optimizationCandidates()).isEqualTo(1);
		assertThat(s.storedCount()).isEqualTo(4); // excluded works stay in the snapshot with evidence
		assertThat(jdbc.sql("""
				SELECT relevance_evidence->>'aspectCues' FROM snapshot_membership m JOIN publication p ON p.id = m.publication_id
				WHERE p.openalex_id = 'W2'
				""").query(String.class).single()).contains("ethic");
	}

	@Test
	void frozenInputsMatchTheNormalizerOutputExactly() {
		WorkJson w = work("W1", "Deep Reinforcement Learning for Robotic Manipulation").year(2017).doi("10.1/x")
				.author("A1", "Shixiang Gu").cites("W9");
		SnapshotSummary s = snapshot(Q, w);

		FrozenCandidate frozen = service.optimizationCandidates(s.id()).getFirst();
		assertThat(frozen.work())
				.isEqualTo(((Accepted) new OpenAlexWorkNormalizer().normalize(w.json())).work());
		assertThat(frozen.providerRank()).isEqualTo(1);
		assertThat(frozen.relevanceEvidence().path("titleOrConceptCoverage").doubleValue()).isEqualTo(1.0);
	}

	@Test
	void frozenInputsSurviveALaterCacheRefresh() {
		SnapshotSummary first = snapshot(Q, work("W1", "Reinforcement Learning for Robot Control").year(2001));
		snapshot("robot reinforcement learning", work("W1", "Reinforcement Learning for Robot Control (revised)").year(2002));

		assertThat(jdbc.sql("SELECT title FROM publication WHERE openalex_id = 'W1'").query(String.class).single())
				.isEqualTo("Reinforcement Learning for Robot Control (revised)"); // cache refreshed
		FrozenCandidate old = service.optimizationCandidates(first.id()).getFirst();
		assertThat(old.work().title()).isEqualTo("Reinforcement Learning for Robot Control"); // snapshot unchanged
		assertThat(old.work().publicationYear()).isEqualTo(2001);
	}

	@Test
	void suppressedDuplicatesAreNotOptimizationCandidates() {
		SnapshotSummary s = snapshot(Q,
				work("W1", "Reinforcement learning in robotics: a survey").year(2013).doi("10.1/a").author("A1", "Jens Kober"),
				work("W2", "Reinforcement learning in robotics: a survey").year(2013).author("A1", "Jens Kober"));

		assertThat(s.relevance().eligible()).isEqualTo(2);
		assertThat(s.relevance().optimizationCandidates()).isEqualTo(1);
		assertThat(service.optimizationCandidates(s.id())).extracting(c -> c.work().openalexId()).containsExactly("W1");
	}

	@Test
	void emptyAndNarrowResultsCarryWarnings() {
		SnapshotSummary empty = snapshot("zzqx unknown subject");
		assertThat(empty.storedCount()).isZero();
		assertThat(empty.relevance().warnings().toString()).contains("NO_ELIGIBLE_CANDIDATES");

		SnapshotSummary few = snapshot(Q, work("W1", "Reinforcement learning for robot navigation"));
		assertThat(few.relevance().warnings().toString()).contains("FEW_ELIGIBLE_CANDIDATES").doesNotContain("BROAD");

		SnapshotSummary broad = snapshot("robotics", work("W2", "Robotics"));
		assertThat(broad.relevance().warnings().toString()).contains("BROAD_QUERY");
	}

	@Test
	void relevanceDiagnosticsListDecisionsWithEvidenceAndFilterByTier() {
		SnapshotSummary s = snapshot(Q, work("W1", "Off-Policy Reinforcement Learning for Robotics"),
				work("W2", "Ethical considerations of reinforcement learning in robotics"));

		List<RelevanceRow> all = service.relevance(s.id(), null).orElseThrow();
		assertThat(all).extracting(RelevanceRow::openalexId, RelevanceRow::tier, RelevanceRow::optimizationCandidate)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("W1", "ELIGIBLE", true),
						org.assertj.core.groups.Tuple.tuple("W2", "TANGENTIAL", false));
		assertThat(all.get(1).evidence().path("aspectCues").toString()).contains("ethic");
		assertThat(service.relevance(s.id(), dev.researchpath.relevance.RelevanceTier.TANGENTIAL).orElseThrow())
				.extracting(RelevanceRow::openalexId).containsExactly("W2");
		assertThat(service.relevance(java.util.UUID.randomUUID(), null)).isEmpty();
	}

	@Test
	void stopwordOnlyTopicsAreRejectedBeforeAnyProviderCall() {
		assertThatThrownBy(() -> service.createSnapshot("the of and", 10))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("meaningful word");
		verify(openAlex, never()).searchWorks(anyString(), anyInt(), anyInt());
	}

	@Test
	void sameInputsGiveTheSameEligibleCandidates() {
		WorkJson[] works = { work("W1", "Reinforcement Learning for Robot Control"), work("W2", "Protein folding"),
				work("W3", "Deep RL for robotic grasping") };
		List<Long> a = service.optimizationCandidates(snapshot(Q, works).id()).stream().map(FrozenCandidate::publicationId).toList();
		List<Long> b = service.optimizationCandidates(snapshot(Q, works).id()).stream().map(FrozenCandidate::publicationId).toList();
		assertThat(a).isEqualTo(b).hasSize(2);
	}

}
