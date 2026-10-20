package dev.researchpath.path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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

/** S02 retention: unviewed paths and unused snapshots are deleted whole; nothing in use is touched. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RetentionServiceTests {

	@Autowired
	PathService paths;

	@Autowired
	RetentionService retention;

	@Autowired
	CandidateSnapshotService snapshots;

	@Autowired
	JdbcClient jdbc;

	@MockitoBean
	OpenAlexClient openAlex;

	@BeforeEach
	void setUp() {
		jdbc.sql("TRUNCATE candidate_snapshot, publication, concept RESTART IDENTITY CASCADE").update();
		when(openAlex.searchWorks(eq(PathServiceTests.Q), eq(1), anyInt(), any())).thenReturn(PathServiceTests.pool(""));
	}

	private UUID internalId(String publicId) {
		return jdbc.sql("SELECT id FROM reading_path WHERE public_id = :p").param("p", publicId).query(UUID.class).single();
	}

	private void lastViewed(String publicId, Instant at) {
		jdbc.sql("UPDATE path_access SET last_viewed_at = :t WHERE path_id = :id").param("t", Timestamp.from(at))
				.param("id", internalId(publicId)).update();
	}

	private int count(String table) {
		return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
	}

	@Test
	void unviewedPathsAreDeletedWithAllRevisionsAndRecentlyViewedOnesStay() {
		Instant now = Instant.now();
		PathService.PathView old = paths.create(PathServiceTests.Q, PathOptions.DEFAULT, null);
		paths.regenerate(old.pathId(), new PathOptions(Preferences().experience(), Preferences().focus(), 5), null);
		PathService.PathView recent = paths.create(PathServiceTests.Q, PathOptions.DEFAULT, null);
		lastViewed(old.pathId(), now.minus(Duration.ofDays(91)));
		lastViewed(recent.pathId(), now.minus(Duration.ofDays(10)));
		int itemsOfRecent = jdbc.sql("""
				SELECT count(*) FROM path_revision_item i JOIN path_revision r ON r.id = i.revision_id
				WHERE r.path_id = :id""").param("id", internalId(recent.pathId())).query(Integer.class).single();

		RetentionService.Purge purge = retention.purge(now);

		assertThat(purge.paths()).isEqualTo(1);
		assertThat(jdbc.sql("SELECT public_id FROM reading_path").query(String.class).list()).containsExactly(recent.pathId());
		assertThat(count("path_revision")).isEqualTo(1);
		assertThat(count("path_revision_item")).isEqualTo(itemsOfRecent);
		assertThat(count("path_access")).isEqualTo(1);
	}

	@Test
	void snapshotsInUseAreKeptAndUnusedOldOnesAreDeleted() {
		Instant now = Instant.now();
		PathService.PathView used = paths.create(PathServiceTests.Q, PathOptions.DEFAULT, null);
		UUID unusedOld = snapshots.createSnapshot(PathServiceTests.Q, null).id();
		UUID unusedRecent = snapshots.createSnapshot(PathServiceTests.Q, null).id();
		jdbc.sql("UPDATE candidate_snapshot SET created_at = :t WHERE id <> :recent")
				.param("t", Timestamp.from(now.minus(Duration.ofDays(31)))).param("recent", unusedRecent).update();
		int publications = count("publication");

		RetentionService.Purge purge = retention.purge(now);

		assertThat(purge.paths()).isZero();
		assertThat(purge.snapshots()).isEqualTo(1);
		assertThat(jdbc.sql("SELECT id FROM candidate_snapshot").query(UUID.class).list()).doesNotContain(unusedOld)
				.contains(unusedRecent).hasSize(2);
		assertThat(jdbc.sql("SELECT count(*) FROM snapshot_membership WHERE snapshot_id = :id").param("id", unusedOld)
				.query(Integer.class).single()).isZero();
		assertThat(count("publication")).as("the publication cache is kept").isEqualTo(publications);
		assertThat(paths.get(used.pathId(), null).revision()).isEqualTo(1);
	}

	@Test
	void viewsRecordAccessAtMostHourlyAndResponsesStateThePolicy() {
		PathService.PathView v = paths.create(PathServiceTests.Q, PathOptions.DEFAULT, null);
		assertThat(v.body().path("retention").path("deleteAfterDaysUnviewed").asInt()).isEqualTo(90);
		assertThat(v.body().path("retention").path("notice").asString()).contains("90 days");

		UUID id = internalId(v.pathId());
		Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
		jdbc.sql("UPDATE path_access SET last_viewed_at = :t WHERE path_id = :id").param("t", Timestamp.from(t0)).param("id", id).update();
		retention.touch(id, t0.plus(Duration.ofMinutes(30)));
		assertThat(lastViewedAt(id)).as("within the hour: no write").isEqualTo(t0);
		retention.touch(id, t0.plus(Duration.ofHours(2)));
		assertThat(lastViewedAt(id)).isEqualTo(t0.plus(Duration.ofHours(2)));

		paths.get(v.pathId(), null);
		assertThat(lastViewedAt(id)).as("opening the path refreshes it").isAfter(t0.plus(Duration.ofDays(200)));
		// The stored, immutable document does not contain the policy.
		assertThat(jdbc.sql("SELECT result::text FROM path_revision").query(String.class).single()).doesNotContain("retention");
	}

	private Instant lastViewedAt(UUID id) {
		return jdbc.sql("SELECT last_viewed_at FROM path_access WHERE path_id = :id").param("id", id)
				.query(Timestamp.class).single().toInstant();
	}

	private static dev.researchpath.optimizer.Preferences Preferences() {
		return new dev.researchpath.optimizer.Preferences(dev.researchpath.optimizer.Preferences.Experience.ADVANCED,
				dev.researchpath.optimizer.Preferences.Focus.RECENT);
	}

}
