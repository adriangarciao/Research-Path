package dev.researchpath.snapshot;

import static dev.researchpath.snapshot.WorkJson.page;
import static dev.researchpath.snapshot.WorkJson.work;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.researchpath.TestcontainersConfiguration;
import dev.researchpath.identity.IdentityDiagnostics;
import dev.researchpath.identity.IdentityRepository;
import dev.researchpath.openalex.OpenAlexClient;

/** M05: conservative identity resolution applied and frozen at snapshot creation (real PostgreSQL 17). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SnapshotIdentityResolutionTests {

	private static final String TITLE = "A Comprehensive Survey on Graph Neural Networks";

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
		when(openAlex.searchWorks(eq(topic), eq(1), anyInt(), any())).thenReturn(page(1, works.length, works));
		return service.createSnapshot(topic, 20);
	}

	/** openalexId -> canonical openalexId for one snapshot. */
	private Map<String, String> canonical(UUID snapshotId) {
		return jdbc.sql("""
				SELECT p.openalex_id, c.openalex_id FROM snapshot_membership m
				JOIN publication p ON p.id = m.publication_id JOIN publication c ON c.id = m.canonical_publication_id
				WHERE m.snapshot_id = :id
				""").param("id", snapshotId)
				.query((rs, n) -> Map.entry(rs.getString(1), rs.getString(2))).list().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private String decision(UUID snapshotId) {
		// One work pair can have two evidence rows (SAME_DOI and SAME_NORMALIZED_TITLE); they must agree.
		return jdbc.sql("SELECT DISTINCT decision || ':' || rule FROM snapshot_identity_decision WHERE snapshot_id = :id")
				.param("id", snapshotId).query(String.class).single();
	}

	@Test
	void obviousDuplicateIsSuppressedButKeptAsProvenance() {
		SnapshotSummary s = snapshot("gnn",
				work("W2907492528", TITLE).year(2020).doi("10.1109/tnnls.2020.2978386").author("A1", "Zonghan Wu"),
				work("W4210257598", TITLE).year(2020).author("A9", "Wu, Z"), // repository copy, no DOI
				work("W3", "Something else").year(2020).author("A1", "Zonghan Wu"));

		assertThat(canonical(s.id())).isEqualTo(Map.of(
				"W2907492528", "W2907492528", "W4210257598", "W2907492528", "W3", "W3"));
		assertThat(decision(s.id())).isEqualTo("SAME_WORK:TITLE_AUTHORS_YEAR");
		assertThat(s.identity()).isEqualTo(new SnapshotSummary.Identity(1, 1, 0, 0, 0, 2, 1));
		assertThat(s.storedCount()).isEqualTo(3); // nothing deleted
		assertThat(jdbc.sql("""
				SELECT source_record->>'id' FROM snapshot_membership m JOIN publication p ON p.id = m.publication_id
				WHERE p.openalex_id = 'W4210257598'
				""").query(String.class).single()).isEqualTo("https://openalex.org/W4210257598");
	}

	@Test
	void exactDoiAndTitleCollapseToTheLowestIdWhenOtherwiseEqual() {
		SnapshotSummary s = snapshot("doi",
				work("W20", TITLE).year(2020).doi("10.1/same").author("A1", "Ann Lee"),
				work("W10", TITLE).year(2021).doi("10.1/SAME").author("A2", "Bo Chen"));

		assertThat(decision(s.id())).isEqualTo("SAME_WORK:EXACT_DOI");
		assertThat(canonical(s.id())).isEqualTo(Map.of("W20", "W10", "W10", "W10"));
		assertThat(jdbc.sql("SELECT count(*) FROM snapshot_identity_decision").query(Long.class).single())
				.isEqualTo(2); // both evidence rows decided...
		assertThat(s.identity().sameWorkPairs()).isEqualTo(1); // ...but counted once as a work pair
	}

	@Test
	void versionsAndDifferentWorksWithTheSameTitleStaySeparate() {
		SnapshotSummary s = snapshot("versions",
				work("W1", TITLE).year(2019).doi("10.48550/arxiv.1").author("A1", "Ann Lee"), // preprint
				work("W2", TITLE).year(2020).doi("10.1/journal").author("A1", "Ann Lee"), // journal version
				work("W3", TITLE).year(2020).author("A7", "Someone Else")); // unrelated, same title

		assertThat(canonical(s.id())).isEqualTo(Map.of("W1", "W1", "W2", "W2", "W3", "W3"));
		assertThat(s.identity()).isEqualTo(new SnapshotSummary.Identity(1, 0, 1, 2, 0, 3, 0));
	}

	@Test
	void decisionsAreFrozenWhenTheCacheChangesLater() {
		SnapshotSummary first = snapshot("first",
				work("W1", TITLE).year(2020).doi("10.1/a").author("A1", "Ann Lee"),
				work("W2", TITLE).year(2020).author("A1", "Ann Lee"));
		// Later the provider gives W2 its own, different DOI: now it looks like a separate version.
		SnapshotSummary second = snapshot("second",
				work("W1", TITLE).year(2020).doi("10.1/a").author("A1", "Ann Lee"),
				work("W2", TITLE).year(2020).doi("10.1/b").author("A1", "Ann Lee"));

		assertThat(canonical(second.id())).isEqualTo(Map.of("W1", "W1", "W2", "W2"));
		assertThat(decision(second.id())).isEqualTo("PROBABLE_VERSION:LIKELY_VERSION");
		// The first snapshot keeps the decision it was created with.
		assertThat(canonical(first.id())).isEqualTo(Map.of("W1", "W1", "W2", "W1"));
		assertThat(decision(first.id())).isEqualTo("SAME_WORK:TITLE_AUTHORS_YEAR");
	}

	@Test
	void reingestingTheSameDataIsIdempotent() {
		WorkJson[] works = { work("W1", TITLE).year(2020).doi("10.1/a").author("A1", "Ann Lee"),
				work("W2", TITLE).year(2020).author("A1", "Ann Lee") };
		SnapshotSummary a = snapshot("same", works);
		SnapshotSummary b = snapshot("same", works);

		assertThat(canonical(a.id())).isEqualTo(canonical(b.id()));
		assertThat(jdbc.sql("SELECT count(*) FROM publication_identity_candidate").query(Long.class).single())
				.isEqualTo(1);
		assertThat(jdbc.sql("SELECT count(*) FROM publication").query(Long.class).single()).isEqualTo(2);
	}

	@Test
	void everyCanonicalIsAMemberOfTheSameSnapshot() {
		snapshot("alpha topic", work("W1", TITLE).year(2020).doi("10.1/a").author("A1", "Ann Lee"),
				work("W2", TITLE).year(2020).author("A1", "Ann Lee"), work("W3", "x").year(2001));
		snapshot("beta topic", work("W2", TITLE).year(2020).author("A1", "Ann Lee"), work("W4", "y"));

		assertThat(jdbc.sql("""
				SELECT count(*) FROM snapshot_membership m
				WHERE m.canonical_publication_id IS NULL
				   OR NOT EXISTS (SELECT 1 FROM snapshot_membership c
				                  WHERE c.snapshot_id = m.snapshot_id AND c.publication_id = m.canonical_publication_id)
				""").query(Long.class).single()).isZero();
	}

	@Autowired
	IdentityRepository identityRepository;

	@Test
	void diagnosticsListFrozenDecisionsWithEvidenceAndSuppressedMembers() {
		SnapshotSummary s = snapshot("diag",
				work("W1", TITLE).year(2020).doi("10.1/a").author("A1", "Ann Lee"),
				work("W2", TITLE).year(2020).author("A1", "Ann Lee"),
				work("W3", TITLE).year(2020).author("A7", "Other Person"));

		IdentityDiagnostics d = identityRepository.diagnostics(s.id()).orElseThrow();
		assertThat(d.policyVersion()).isEqualTo(1);
		assertThat(d.decisions()).extracting(IdentityDiagnostics.Decision::decision)
				.containsExactly("SAME_WORK", "DISTINCT", "DISTINCT"); // SAME_WORK listed first
		IdentityDiagnostics.Decision same = d.decisions().get(0);
		assertThat(same.rule()).isEqualTo("TITLE_AUTHORS_YEAR");
		assertThat(same.details().path("sharedAuthorIds").intValue()).isEqualTo(1);
		assertThat(same.a().openalexId()).isEqualTo("W1");
		assertThat(d.suppressed()).singleElement().satisfies(x -> {
			assertThat(x.openalexId()).isEqualTo("W2");
			assertThat(x.canonicalOpenalexId()).isEqualTo("W1");
		});
		assertThat(identityRepository.diagnostics(UUID.randomUUID())).isEmpty();
	}

	@Test
	void snapshotsCreatedBeforeIdentityResolutionReportNoIdentityBlock() {
		UUID legacy = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO candidate_snapshot (id, topic, normalized_topic, provider, retrieval_params, requested_count,
				    received_count, stored_count, rejected_count, normalizer_version, created_at)
				VALUES (:id, 'legacy', 'legacy', 'OPENALEX', '{}'::jsonb, 1, 0, 0, 0, 1, :at)
				""").param("id", legacy).param("at", Timestamp.from(Instant.parse("2026-10-17T12:00:00Z"))).update();

		assertThat(service.findSummary(legacy).orElseThrow().identity()).isNull();
		IdentityDiagnostics d = identityRepository.diagnostics(legacy).orElseThrow();
		assertThat(d.policyVersion()).isNull();
		assertThat(d.decisions()).isEmpty();
	}

}
