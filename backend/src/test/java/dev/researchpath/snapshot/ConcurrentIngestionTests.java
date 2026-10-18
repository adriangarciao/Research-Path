package dev.researchpath.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.researchpath.TestcontainersConfiguration;
import dev.researchpath.openalex.OpenAlexClient;

/** M13 DB fault: several snapshots ingesting the same works at once (publication upsert race). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ConcurrentIngestionTests {

	@Autowired
	CandidateSnapshotService snapshots;

	@Autowired
	JdbcClient jdbc;

	@MockitoBean
	OpenAlexClient openAlex;

	@Test
	void concurrentSnapshotsOfTheSameWorksDoNotDuplicateOrLoseData() throws Exception {
		jdbc.sql("TRUNCATE candidate_snapshot, publication, concept RESTART IDENTITY CASCADE").update();
		List<WorkJson> works = new ArrayList<>();
		for (int i = 1; i <= 30; i++) {
			works.add(WorkJson.work("W" + i, "Reinforcement learning in robotics " + i).year(2010 + i % 15)
					.author("A" + i, "Author " + i).cites("W" + (i % 30 + 1)));
		}
		when(openAlex.searchWorks(anyString(), eq(1), anyInt()))
				.thenAnswer(inv -> WorkJson.page(1, 30, works.toArray(WorkJson[]::new)));

		int n = 6;
		ExecutorService pool = Executors.newFixedThreadPool(n);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<UUID>> futures = new ArrayList<>();
			for (int t = 0; t < n; t++) {
				futures.add(pool.submit(() -> {
					start.await();
					return snapshots.createSnapshot("reinforcement learning in robotics", 30).id();
				}));
			}
			start.countDown();
			List<UUID> ids = new ArrayList<>();
			for (Future<UUID> f : futures) {
				ids.add(f.get());
			}
			assertThat(ids).doesNotHaveDuplicates().hasSize(n);
		} finally {
			pool.shutdownNow();
		}

		assertThat(jdbc.sql("SELECT count(*) FROM publication").query(Integer.class).single()).isEqualTo(30);
		assertThat(jdbc.sql("SELECT count(*) FROM snapshot_membership").query(Integer.class).single()).isEqualTo(30 * n);
		assertThat(jdbc.sql("SELECT count(DISTINCT snapshot_id) FROM snapshot_membership").query(Integer.class).single())
				.isEqualTo(n);
		// Every snapshot has the same frozen, evaluated pool.
		List<Integer> eligible = jdbc.sql("""
				SELECT count(*) FROM snapshot_membership WHERE eligibility_status = 'ELIGIBLE' GROUP BY snapshot_id
				""").query(Integer.class).list();
		assertThat(eligible).hasSize(n).containsOnly(eligible.get(0));
	}

}
