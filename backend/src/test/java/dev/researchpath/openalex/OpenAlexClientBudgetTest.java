package dev.researchpath.openalex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

/** H2: every provider attempt reserves budget first; nothing is sent once it is used up. */
class OpenAlexClientBudgetTest {

	private static final String PAGE = "{\"meta\":{\"count\":1},\"results\":[{\"id\":\"https://openalex.org/W1\"}]}";
	private static final String WORKS = "https://api.openalex.test/works?";

	/** Counts reservations and refunds against a fixed limit. */
	static final class FakeBudget implements CallBudget {
		int limit;
		int used;
		int refunds;

		FakeBudget(int limit) {
			this.limit = limit;
		}

		@Override
		public boolean tryReserve() {
			if (used >= limit) {
				return false;
			}
			used++;
			return true;
		}

		@Override
		public void refund() {
			refunds++;
			used = Math.max(0, used - 1);
		}

		@Override
		public long secondsUntilReset() {
			return 1234;
		}
	}

	private MockRestServiceServer server;
	private final List<Duration> sleeps = new ArrayList<>();

	private OpenAlexClient client(int maxAttempts, CallBudget budget) {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openalex.test");
		server = MockRestServiceServer.bindTo(builder).build();
		OpenAlexProperties props = new OpenAlexProperties("https://api.openalex.test", "secret-key", Duration.ofSeconds(1),
				Duration.ofSeconds(1), maxAttempts, Duration.ofSeconds(1), Duration.ofSeconds(30), 100, DataSize.ofMegabytes(8));
		return new OpenAlexClient(builder.build(), props, JsonMapper.builder().build(), sleeps::add,
				Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC), budget);
	}

	@Test
	void eachAttemptIncludingRetriesReservesOneCall() {
		FakeBudget budget = new FakeBudget(10);
		OpenAlexClient client = client(3, budget);
		server.expect(requestTo(Matchers.startsWith(WORKS))).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		server.expect(requestTo(Matchers.startsWith(WORKS))).andRespond(withSuccess(PAGE, MediaType.APPLICATION_JSON));
		assertThat(client.searchWorks("x", 1, 1).results()).hasSize(1);
		assertThat(budget.used).isEqualTo(2);
	}

	@Test
	void exhaustedBudgetSendsNothing() {
		FakeBudget budget = new FakeBudget(0);
		OpenAlexClient client = client(3, budget);
		server.expect(never(), requestTo(Matchers.startsWith(WORKS)));
		assertThatThrownBy(() -> client.searchWorks("x", 1, 1))
				.isInstanceOfSatisfying(ProviderBudgetExhaustedException.class, e -> assertThat(e.retryAfterSeconds()).isEqualTo(1234));
		server.verify();
	}

	@Test
	void budgetRunningOutBetweenRetriesStopsTheRetries() {
		FakeBudget budget = new FakeBudget(1);
		OpenAlexClient client = client(3, budget);
		server.expect(times(1), requestTo(Matchers.startsWith(WORKS))).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		assertThatThrownBy(() -> client.searchWorks("x", 1, 1)).isInstanceOf(ProviderBudgetExhaustedException.class);
		server.verify();
	}

	@Test
	void connectFailuresAreRefundedButReadTimeoutsAreNot() {
		FakeBudget budget = new FakeBudget(10);
		OpenAlexClient client = client(2, budget);
		server.expect(requestTo(Matchers.startsWith(WORKS))).andRespond(withException(new ConnectException("refused")));
		server.expect(requestTo(Matchers.startsWith(WORKS))).andRespond(withSuccess(PAGE, MediaType.APPLICATION_JSON));
		client.searchWorks("x", 1, 1);
		assertThat(budget.refunds).isEqualTo(1);
		assertThat(budget.used).as("only the successful attempt counts").isEqualTo(1);

		FakeBudget timed = new FakeBudget(10);
		OpenAlexClient other = client(2, timed);
		server.expect(requestTo(Matchers.startsWith(WORKS))).andRespond(withException(new SocketTimeoutException("read timed out")));
		server.expect(requestTo(Matchers.startsWith(WORKS))).andRespond(withSuccess(PAGE, MediaType.APPLICATION_JSON));
		other.searchWorks("x", 1, 1);
		assertThat(timed.refunds).as("a read timeout may have been billed").isZero();
		assertThat(timed.used).isEqualTo(2);
	}

	@Test
	void neverConnectedRecognizesOnlyPreConnectionFailures() {
		assertThat(OpenAlexClient.neverConnected(new RuntimeException(new ConnectException()))).isTrue();
		assertThat(OpenAlexClient.neverConnected(new java.net.UnknownHostException("x"))).isTrue();
		assertThat(OpenAlexClient.neverConnected(new java.net.http.HttpConnectTimeoutException("x"))).isTrue();
		assertThat(OpenAlexClient.neverConnected(new SocketTimeoutException("read"))).isFalse();
		assertThat(OpenAlexClient.neverConnected(new java.io.IOException("reset"))).isFalse();
	}

}
