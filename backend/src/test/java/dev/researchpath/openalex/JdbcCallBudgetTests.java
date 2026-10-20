package dev.researchpath.openalex;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.researchpath.TestcontainersConfiguration;

/** H2: the provider budget is durable, atomic under concurrency and resets at midnight UTC. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JdbcCallBudgetTests {

	@Autowired
	JdbcClient jdbc;

	@MockitoBean
	OpenAlexClient openAlex;

	private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-20T23:00:00Z"));

	private final Clock clock = new Clock() {
		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now.get();
		}
	};

	private JdbcCallBudget budget(int limit) {
		return new JdbcCallBudget(jdbc, new ProviderBudgetProperties(true, limit), clock);
	}

	@BeforeEach
	void clean() {
		jdbc.sql("DELETE FROM provider_call_budget").update();
	}

	@Test
	void reservesUpToTheLimitThenRefusesAndResetsAtMidnightUtc() {
		JdbcCallBudget b = budget(3);
		assertThat(b.tryReserve()).isTrue();
		assertThat(b.tryReserve()).isTrue();
		assertThat(b.tryReserve()).isTrue();
		assertThat(b.tryReserve()).as("exhausted").isFalse();
		assertThat(b.tryReserve()).isFalse();
		assertThat(b.usedToday()).isEqualTo(3);
		assertThat(b.secondsUntilReset()).isEqualTo(3600);

		now.set(Instant.parse("2026-10-21T00:00:01Z"));
		assertThat(b.tryReserve()).as("new UTC day").isTrue();
		assertThat(b.usedToday()).isEqualTo(1);
	}

	@Test
	void concurrentReservationsNeverShareTheRemainingBudget() throws Exception {
		JdbcCallBudget b = budget(25);
		int threads = 60;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<Boolean>> results = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				// A separate instance per thread, like separate requests (or processes) sharing the database.
				JdbcCallBudget own = budget(25);
				results.add(pool.submit(() -> {
					start.await();
					return own.tryReserve();
				}));
			}
			start.countDown();
			int granted = 0;
			for (Future<Boolean> f : results) {
				granted += f.get() ? 1 : 0;
			}
			assertThat(granted).isEqualTo(25);
			assertThat(b.usedToday()).isEqualTo(25);
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void theCountSurvivesARestart() {
		JdbcCallBudget before = budget(5);
		before.tryReserve();
		before.tryReserve();
		JdbcCallBudget afterRestart = budget(5); // new process, same database
		assertThat(afterRestart.usedToday()).isEqualTo(2);
		assertThat(afterRestart.tryReserve()).isTrue();
		assertThat(afterRestart.tryReserve()).isTrue();
		assertThat(afterRestart.tryReserve()).isTrue();
		assertThat(afterRestart.tryReserve()).isFalse();
	}

	@Test
	void refundsGiveBackOnlyTodaysReservationsAndNeverGoNegative() {
		JdbcCallBudget b = budget(2);
		b.tryReserve();
		b.tryReserve();
		b.refund();
		assertThat(b.usedToday()).isEqualTo(1);
		b.refund();
		b.refund();
		assertThat(b.usedToday()).isZero();
		assertThat(jdbc.sql("SELECT min(calls) FROM provider_call_budget").query(Integer.class).single()).isZero();
	}

	@Test
	void disabledBudgetNeverRefusesAndRecordsNothing() {
		JdbcCallBudget off = new JdbcCallBudget(jdbc, new ProviderBudgetProperties(false, 0), clock);
		for (int i = 0; i < 5; i++) {
			assertThat(off.tryReserve()).isTrue();
		}
		assertThat(jdbc.sql("SELECT count(*) FROM provider_call_budget").query(Integer.class).single()).isZero();
	}

}
