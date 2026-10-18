package dev.researchpath.path;

import static dev.researchpath.snapshot.WorkJsonAccess.page;
import static dev.researchpath.snapshot.WorkJsonAccess.work;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import dev.researchpath.TestcontainersConfiguration;
import dev.researchpath.openalex.OpenAlexClient;
import dev.researchpath.openalex.OpenAlexSearchPage;
import dev.researchpath.optimizer.Preferences.Experience;
import dev.researchpath.optimizer.Preferences.Focus;
import dev.researchpath.snapshot.CandidateSnapshotService;
import dev.researchpath.snapshot.WorkJsonAccess;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** M11: stored revisions, lineage and regeneration from the original frozen snapshot. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PathServiceTests {

	static final String Q = "reinforcement learning in robotics";

	@Autowired
	PathService paths;

	@Autowired
	CandidateSnapshotService snapshots;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JsonMapper json;

	@Autowired
	JdbcClient jdbc;

	@MockitoSpyBean
	PathRepository repository;

	@MockitoBean
	OpenAlexClient openAlex;

	/** Twelve relevant works with distinct and shared keywords, a review, and a few citation links. */
	static OpenAlexSearchPage pool(String titlePrefix) {
		List<WorkJsonAccess.Builder> works = new ArrayList<>();
		for (int i = 1; i <= 12; i++) {
			WorkJsonAccess.Builder w = work("W" + i, titlePrefix + "Reinforcement learning in robotics, study " + i)
					.year(2004 + i).citedBy(5 * i).author("A" + i, "Author " + i).doi("10.1/w" + i)
					.keyword("k" + i, "Concept " + i, 0.9).keyword("shared-" + (i % 3), "Shared " + (i % 3), 0.7);
			if (i == 1) {
				w.type("review");
			}
			if (i == 5) {
				w.cites("W2");
			}
			if (i == 8) {
				w.cites("W5", "W2");
			}
			works.add(w);
		}
		return page(1, 12, works.toArray(WorkJsonAccess.Builder[]::new));
	}

	@BeforeEach
	void setUp() {
		reset(repository);
		jdbc.sql("TRUNCATE candidate_snapshot, publication, concept RESTART IDENTITY CASCADE").update();
		when(openAlex.searchWorks(eq(Q), eq(1), anyInt())).thenReturn(pool(""));
	}

	private static PathOptions options(Experience e, Focus f, int budget) {
		return new PathOptions(e, f, budget);
	}

	private JsonNode stored(String pathId, int revision) {
		ObjectNode body = (ObjectNode) paths.get(pathId, revision).body().deepCopy();
		body.remove("revisions");
		return body;
	}

	private int revisionCount() {
		return jdbc.sql("SELECT count(*) FROM path_revision").query(Integer.class).single();
	}

	@Test
	void generatesRetrievesAndRegeneratesFromTheOriginalSnapshotWithoutProviderCalls() {
		PathService.PathView v1 = paths.create(Q, options(Experience.INTERMEDIATE, Focus.BALANCED, 5), null);
		assertThat(v1.created()).isTrue();
		assertThat(v1.revision()).isEqualTo(1);
		assertThat(v1.pathId()).matches(PathService.PUBLIC_ID);
		JsonNode b1 = v1.body();
		assertThat(b1.path("papers").size()).isEqualTo(5);
		assertThat(b1.path("parentRevision").isNull()).isTrue();
		assertThat(b1.path("revisions").size()).isEqualTo(1);
		assertThat(b1.path("access").asString()).contains("Anyone with this link");
		assertThat(b1.path("disclosures").toString()).contains("ORDER_IS_HEURISTIC", "DIFFICULTY_NOT_ESTIMATED");
		assertThat(b1.path("coverage").path("denominator").asString()).contains("eligible candidates examined");
		assertThat(b1.path("snapshot").path("eligibleCandidates").asInt()).isEqualTo(12);
		assertThat(b1.path("versions").path("optimizerConfig").asInt()).isEqualTo(2);
		JsonNode first = b1.path("papers").get(0);
		assertThat(first.path("authors").get(0).asString()).startsWith("Author ");
		assertThat(first.path("reasons").size()).isGreaterThanOrEqualTo(1);
		assertThat(first.path("link").asString()).startsWith("https://doi.org/");
		JsonNode original = stored(v1.pathId(), 1);

		clearInvocations(openAlex);
		PathService.PathView v2 = paths.regenerate(v1.pathId(), options(Experience.ADVANCED, Focus.RECENT, 10), null);

		verify(openAlex, never()).searchWorks(anyString(), anyInt(), anyInt());
		assertThat(v2.created()).isTrue();
		assertThat(v2.revision()).isEqualTo(2);
		assertThat(v2.body().path("parentRevision").asInt()).isEqualTo(1);
		assertThat(v2.body().path("papers").size()).isEqualTo(10);
		assertThat(v2.body().path("options").path("experience").asString()).isEqualTo("ADVANCED");
		assertThat(v2.body().path("snapshot")).isEqualTo(b1.path("snapshot")); // same frozen snapshot
		assertThat(jdbc.sql("SELECT count(DISTINCT snapshot_id) FROM reading_path").query(Integer.class).single()).isEqualTo(1);

		assertThat(stored(v1.pathId(), 1)).isEqualTo(original); // the parent is untouched
		assertThat(paths.get(v1.pathId(), null).revision()).isEqualTo(2);
		assertThat(paths.get(v1.pathId(), null).body().path("revisions").size()).isEqualTo(2);

		List<PathRepository.ItemRow> items = repository.items(jdbc.sql("SELECT id FROM reading_path")
				.query(java.util.UUID.class).single(), 2);
		assertThat(items).extracting(PathRepository.ItemRow::position).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
		List<String> ids = new ArrayList<>();
		v2.body().path("papers").forEach(p -> ids.add(p.path("openalexId").asString()));
		assertThat(items).extracting(PathRepository.ItemRow::openalexId).containsExactlyElementsOf(ids);
	}

	@Test
	void identicalInputsReuseTheStoredRevisionAndSameSnapshotGivesSameContent() {
		PathService.PathView v1 = paths.create(Q, PathOptions.DEFAULT, null);
		PathService.PathView same = paths.regenerate(v1.pathId(), PathOptions.DEFAULT, null);
		assertThat(same.created()).isFalse();
		assertThat(same.revision()).isEqualTo(1);

		PathService.PathView v2 = paths.regenerate(v1.pathId(), options(Experience.BEGINNER, Focus.BALANCED, 5), null);
		PathService.PathView again = paths.regenerate(v1.pathId(), options(Experience.BEGINNER, Focus.BALANCED, 5), 1);
		assertThat(v2.created()).isTrue();
		assertThat(again.created()).isFalse();
		assertThat(again.revision()).isEqualTo(2);
		assertThat(revisionCount()).isEqualTo(2);

		// A second path on an identical frozen pool produces identical papers, stages and coverage.
		PathService.PathView other = paths.create(Q, PathOptions.DEFAULT, null);
		for (String field : List.of("papers", "stages", "coverage", "warnings")) {
			assertThat(other.body().path(field)).as(field).isEqualTo(v1.body().path(field));
		}
	}

	@Test
	void budgetsAreObeyedAndShortPoolsAreReported() {
		PathService.PathView v = paths.create(Q, options(Experience.INTERMEDIATE, Focus.BALANCED, 5), null);
		assertThat(v.body().path("papers").size()).isEqualTo(5);
		PathService.PathView v15 = paths.regenerate(v.pathId(), options(Experience.INTERMEDIATE, Focus.BALANCED, 15), null);
		assertThat(v15.body().path("papers").size()).isEqualTo(12);
		assertThat(v15.body().path("requestedCount").asInt()).isEqualTo(15);
		assertThat(v15.body().path("warnings").toString()).contains("FEWER_THAN_REQUESTED");
		assertThatThrownBy(() -> options(Experience.INTERMEDIATE, Focus.BALANCED, 7))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void failureDuringPersistenceLeavesNoPartialRevisionAndPreservesTheParent() {
		PathService.PathView v1 = paths.create(Q, options(Experience.INTERMEDIATE, Focus.BALANCED, 5), null);
		JsonNode original = stored(v1.pathId(), 1);
		doAnswer(inv -> {
			inv.callRealMethod(); // revision and items written, then the transaction fails
			throw new IllegalStateException("simulated failure after insert");
		}).when(repository).insertRevision(any(), any(), anyInt(), any(), any(), anyString(), anyString(), anyString(),
				any(), anyList());

		assertThatThrownBy(() -> paths.regenerate(v1.pathId(), options(Experience.ADVANCED, Focus.BALANCED, 10), null))
				.hasMessageContaining("simulated");
		assertThatThrownBy(() -> paths.create(Q, PathOptions.DEFAULT, null)).hasMessageContaining("simulated");

		assertThat(revisionCount()).isEqualTo(1);
		assertThat(jdbc.sql("SELECT count(*) FROM path_revision_item").query(Integer.class).single()).isEqualTo(5);
		assertThat(jdbc.sql("SELECT count(*) FROM reading_path").query(Integer.class).single()).isEqualTo(1);
		reset(repository);
		assertThat(stored(v1.pathId(), 1)).isEqualTo(original);
		assertThat(paths.get(v1.pathId(), null).revision()).isEqualTo(1);
	}

	@Test
	void invalidOrUnknownIdsAndRevisionsAreRejectedSafely() {
		PathService.PathView v1 = paths.create(Q, PathOptions.DEFAULT, null);
		assertThatThrownBy(() -> paths.get("not-a-path", null)).isInstanceOf(PathExceptions.NotFound.class);
		assertThatThrownBy(() -> paths.get("'; DROP TABLE reading_path; --", null)).isInstanceOf(PathExceptions.NotFound.class);
		assertThatThrownBy(() -> paths.get(PathService.newPublicId(), null)).isInstanceOf(PathExceptions.NotFound.class);
		assertThatThrownBy(() -> paths.get(v1.pathId(), 99)).isInstanceOf(PathExceptions.NotFound.class);
		assertThatThrownBy(() -> paths.get(v1.pathId(), 0)).isInstanceOf(PathExceptions.NotFound.class);
		assertThatThrownBy(() -> paths.regenerate(v1.pathId(), PathOptions.DEFAULT, 7))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> paths.regenerate(PathService.newPublicId(), PathOptions.DEFAULT, null))
				.isInstanceOf(PathExceptions.NotFound.class);
	}

	@Test
	void concurrentRegenerationsDoNotCorruptOrDuplicateRevisions() throws Exception {
		PathService.PathView v1 = paths.create(Q, options(Experience.INTERMEDIATE, Focus.BALANCED, 5), null);
		PathOptions a = options(Experience.INTERMEDIATE, Focus.BALANCED, 10);
		PathOptions b = options(Experience.INTERMEDIATE, Focus.BALANCED, 15);
		List<PathService.PathView> results = runConcurrently(List.of(
				() -> paths.regenerate(v1.pathId(), a, 1), () -> paths.regenerate(v1.pathId(), a, 1),
				() -> paths.regenerate(v1.pathId(), b, 1), () -> paths.regenerate(v1.pathId(), b, 1)));

		assertThat(revisionCount()).isEqualTo(3);
		assertThat(results.stream().filter(PathService.PathView::created)).hasSize(2);
		assertThat(results.get(0).revision()).isEqualTo(results.get(1).revision());
		assertThat(results.get(2).revision()).isEqualTo(results.get(3).revision());
		assertThat(Set.of(results.get(0).revision(), results.get(2).revision())).containsExactlyInAnyOrder(2, 3);
		assertThat(jdbc.sql("SELECT count(DISTINCT request_fingerprint) FROM path_revision").query(Integer.class).single())
				.isEqualTo(3);
	}

	@Test
	void idempotencyKeyReturnsTheFirstResultWithoutRetrievingAgain() throws Exception {
		when(openAlex.searchWorks(eq(Q), eq(1), anyInt())).thenAnswer(inv -> {
			Thread.sleep(300); // keep the first request in flight while the second arrives
			return pool("");
		});
		String key = "client-key-0001";
		List<PathService.PathView> results = runConcurrently(List.of(
				() -> paths.create(Q, PathOptions.DEFAULT, key), () -> paths.create(Q, PathOptions.DEFAULT, key)));

		assertThat(results.get(0).pathId()).isEqualTo(results.get(1).pathId());
		assertThat(results.stream().filter(PathService.PathView::created)).hasSize(1);
		verify(openAlex, times(1)).searchWorks(eq(Q), eq(1), anyInt());

		PathService.PathView replay = paths.create(Q, PathOptions.DEFAULT, key);
		assertThat(replay.created()).isFalse();
		assertThat(replay.pathId()).isEqualTo(results.get(0).pathId());
		verify(openAlex, times(1)).searchWorks(eq(Q), eq(1), anyInt());
		assertThat(jdbc.sql("SELECT count(*) FROM reading_path").query(Integer.class).single()).isEqualTo(1);

		assertThatThrownBy(() -> paths.create("graph neural networks", PathOptions.DEFAULT, key))
				.isInstanceOf(PathExceptions.Conflict.class);
		assertThatThrownBy(() -> paths.create(Q, PathOptions.DEFAULT, "bad key!"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void refreshingThePublicationCacheDoesNotChangeAHistoricalPath() {
		PathService.PathView v1 = paths.create(Q, PathOptions.DEFAULT, null);
		JsonNode original = stored(v1.pathId(), 1);

		// A later retrieval of the same works with changed titles refreshes the publication cache.
		when(openAlex.searchWorks(eq(Q), eq(1), anyInt())).thenReturn(pool("CHANGED "));
		snapshots.createSnapshot(Q, null);
		assertThat(jdbc.sql("SELECT title FROM publication WHERE openalex_id = 'W1'").query(String.class).single())
				.startsWith("CHANGED");

		PathService.PathView v2 = paths.regenerate(v1.pathId(), options(Experience.ADVANCED, Focus.BALANCED, 10), null);
		v2.body().path("papers").forEach(p -> assertThat(p.path("title").asString()).doesNotStartWith("CHANGED"));
		assertThat(stored(v1.pathId(), 1)).isEqualTo(original);
	}

	@Test
	void storedRowsAreImmutable() {
		paths.create(Q, PathOptions.DEFAULT, null);
		assertThatThrownBy(() -> jdbc.sql("UPDATE path_revision SET budget = 20").update()).hasMessageContaining("immutable");
		assertThatThrownBy(() -> jdbc.sql("UPDATE path_revision_item SET stage = 9").update()).hasMessageContaining("immutable");
		assertThatThrownBy(() -> jdbc.sql("UPDATE reading_path SET topic = 'x'").update()).hasMessageContaining("immutable");
	}

	@Test
	void topicWithoutEligiblePapersStoresNoPath() {
		when(openAlex.searchWorks(eq("sourdough baking"), eq(1), anyInt())).thenReturn(page(1, 2,
				work("W90", "Reinforcement learning in robotics"), work("W91", "Graph neural networks")));
		assertThatThrownBy(() -> paths.create("sourdough baking", PathOptions.DEFAULT, null))
				.isInstanceOf(PathExceptions.NoEligiblePapers.class);
		assertThat(jdbc.sql("SELECT count(*) FROM reading_path").query(Integer.class).single()).isZero();
	}

	@Test
	void revisionFanOutAndConcurrentGenerationsAreBounded() {
		PathService limited = new PathService(snapshots, repository, transaction, json, new PathProperties(4, 2));
		PathService.PathView v1 = limited.create(Q, options(Experience.INTERMEDIATE, Focus.BALANCED, 5), null);
		limited.regenerate(v1.pathId(), options(Experience.INTERMEDIATE, Focus.BALANCED, 10), null);
		assertThatThrownBy(() -> limited.regenerate(v1.pathId(), options(Experience.INTERMEDIATE, Focus.BALANCED, 15), null))
				.isInstanceOf(PathExceptions.Conflict.class).hasMessageContaining("limit of 2 revisions");

		PathService saturated = new PathService(snapshots, repository, transaction, json, new PathProperties(0, 50));
		clearInvocations(openAlex);
		assertThatThrownBy(() -> saturated.create(Q, PathOptions.DEFAULT, null)).isInstanceOf(PathExceptions.Busy.class);
		// New options need generation capacity (an already stored revision would be returned without work).
		assertThatThrownBy(() -> saturated.regenerate(v1.pathId(), options(Experience.ADVANCED, Focus.RECENT, 20), null))
				.isInstanceOf(PathExceptions.Busy.class);
		verify(openAlex, never()).searchWorks(anyString(), anyInt(), anyInt());
	}

	private static List<PathService.PathView> runConcurrently(List<Callable<PathService.PathView>> tasks) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<PathService.PathView>> futures = new ArrayList<>();
			for (Callable<PathService.PathView> t : tasks) {
				futures.add(executor.submit(() -> {
					start.await();
					return t.call();
				}));
			}
			start.countDown();
			List<PathService.PathView> out = new ArrayList<>();
			for (Future<PathService.PathView> f : futures) {
				out.add(f.get());
			}
			assertThat(new HashSet<>(out.stream().map(PathService.PathView::pathId).toList())).hasSize(1);
			return out;
		} finally {
			executor.shutdownNow();
		}
	}

}
