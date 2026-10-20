package dev.researchpath.openalex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

/** M7 / R5: one overall time budget bounds every page and retry of a generation. */
class OpenAlexClientDeadlineTest {

	private static final String PAGE = "{\"meta\":{\"count\":1},\"results\":[{\"id\":\"https://openalex.org/W1\"}]}";
	private static final String WORKS = "https://api.openalex.test/works?";

	private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-20T12:00:00Z"));
	private final List<Duration> sleeps = new ArrayList<>();

	/** Simulated time: sleeping advances it, so retry waits count against the deadline. */
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

	private MockRestServiceServer server;

	/** connect 1 s + read 1 s: a worst-case attempt takes 2 s. */
	private static OpenAlexProperties props(String baseUrl, int maxAttempts, Duration readTimeout) {
		return new OpenAlexProperties(baseUrl, "secret-key", Duration.ofSeconds(1), readTimeout, maxAttempts,
				Duration.ofSeconds(1), Duration.ofSeconds(30), 100, DataSize.ofMegabytes(8), Duration.ofSeconds(30));
	}

	private OpenAlexClient client(int maxAttempts, OpenAlexClientBudgetTest.FakeBudget budget) {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openalex.test");
		server = MockRestServiceServer.bindTo(builder).build();
		return new OpenAlexClient(builder.build(), props("https://api.openalex.test", maxAttempts, Duration.ofSeconds(1)),
				JsonMapper.builder().build(), d -> {
					sleeps.add(d);
					now.set(now.get().plus(d));
				}, clock, budget);
	}

	private ProviderDeadline in(long seconds) {
		return ProviderDeadline.after(clock, Duration.ofSeconds(seconds));
	}

	@Test
	void retriesStopOnceTheNextWaitPlusAttemptNoLongerFits() {
		OpenAlexClientBudgetTest.FakeBudget budget = new OpenAlexClientBudgetTest.FakeBudget(100);
		OpenAlexClient client = client(10, budget);
		// t=0 attempt, wait 1 s (1+2 <= 5), t=1 attempt, wait 2 s (2+2 <= 4), t=3 attempt, wait 4 s (4+2 > 2): stop.
		server.expect(times(3), requestTo(Matchers.startsWith(WORKS))).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		assertThatThrownBy(() -> client.searchWorks("x", 1, 1, in(5)))
				.isInstanceOf(ProviderTimeBudgetExceededException.class)
				.isInstanceOf(OpenAlexException.class);
		server.verify();
		assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
		assertThat(budget.used).as("only the attempts actually sent").isEqualTo(3);
	}

	@Test
	void aRetryAfterBeyondTheDeadlineFailsAtOnceInsteadOfWaiting() {
		OpenAlexClient client = client(3, new OpenAlexClientBudgetTest.FakeBudget(100));
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.RETRY_AFTER, "20");
		server.expect(times(1), requestTo(Matchers.startsWith(WORKS)))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));
		assertThatThrownBy(() -> client.searchWorks("x", 1, 1, in(10))).isInstanceOf(ProviderTimeBudgetExceededException.class);
		server.verify();
		assertThat(sleeps).as("no pointless wait").isEmpty();
	}

	@Test
	void anExpiredDeadlineSendsNothingAndReservesNothing() {
		OpenAlexClientBudgetTest.FakeBudget budget = new OpenAlexClientBudgetTest.FakeBudget(100);
		OpenAlexClient client = client(3, budget);
		server.expect(never(), requestTo(Matchers.startsWith(WORKS)));
		assertThatThrownBy(() -> client.searchWorks("x", 1, 1, in(1))).isInstanceOf(ProviderTimeBudgetExceededException.class);
		server.verify();
		assertThat(budget.used).isZero();
	}

	@Test
	void theDeadlineIsSharedAcrossPages() {
		OpenAlexClient client = client(3, new OpenAlexClientBudgetTest.FakeBudget(100));
		ProviderDeadline deadline = in(5);
		server.expect(times(1), requestTo(Matchers.startsWith(WORKS))).andRespond(withSuccess(PAGE, MediaType.APPLICATION_JSON));
		assertThat(client.searchWorks("x", 1, 1, deadline).results()).hasSize(1);
		now.set(now.get().plusSeconds(4)); // page 1 and processing used 4 of the 5 seconds
		assertThatThrownBy(() -> client.searchWorks("x", 2, 1, deadline)).isInstanceOf(ProviderTimeBudgetExceededException.class);
		server.verify();
	}

	@Test
	void withoutADeadlineBehaviourIsUnchanged() {
		OpenAlexClient client = client(3, new OpenAlexClientBudgetTest.FakeBudget(100));
		server.expect(requestTo(Matchers.startsWith(WORKS))).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		server.expect(requestTo(Matchers.startsWith(WORKS))).andRespond(withSuccess(PAGE, MediaType.APPLICATION_JSON));
		assertThat(client.searchWorks("x", 1, 1).results()).hasSize(1);
		assertThat(client.attemptLimit()).isEqualTo(Duration.ofSeconds(2));
	}

	@Test
	void aBudgetShorterThanOneAttemptIsRejectedAtStartup() {
		assertThatThrownBy(() -> new OpenAlexProperties("u", null, Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
				Duration.ofSeconds(1), Duration.ofSeconds(30), 100, DataSize.ofMegabytes(8), Duration.ofSeconds(14)))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("generation-budget");
	}

	/**
	 * The real HTTP stack: the read timeout also bounds a body that trickles in, so one attempt cannot outlast
	 * {@code connectTimeout + readTimeout} and the deadline arithmetic holds.
	 */
	@Test
	void theReadTimeoutBoundsASlowBodyOnTheRealHttpStack() throws Exception {
		HttpServer slow = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		slow.createContext("/works", exchange -> {
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, 0); // chunked
			try (OutputStream out = exchange.getResponseBody()) {
				for (int i = 0; i < 50; i++) {
					out.write("{\"meta\":".getBytes(StandardCharsets.UTF_8));
					out.flush();
					Thread.sleep(200);
				}
			} catch (Exception ignored) {
				// client gave up
			}
		});
		slow.start();
		try {
			String base = "http://127.0.0.1:" + slow.getAddress().getPort();
			OpenAlexProperties props = props(base, 1, Duration.ofMillis(700));
			OpenAlexClient client = new OpenAlexConfiguration().openAlexClient(RestClient.builder(), props,
					JsonMapper.builder().build(), Clock.systemUTC(), CallBudget.UNLIMITED);
			long start = System.nanoTime();
			assertThatThrownBy(() -> client.searchWorks("x", 1, 1)).isInstanceOf(OpenAlexException.class);
			Duration took = Duration.ofNanos(System.nanoTime() - start);
			assertThat(took).as("bounded by the read timeout, not the 10 s trickle").isLessThan(Duration.ofSeconds(3));
		} finally {
			slow.stop(0);
		}
	}

}
