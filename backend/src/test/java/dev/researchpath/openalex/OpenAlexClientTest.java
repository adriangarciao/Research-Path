package dev.researchpath.openalex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

class OpenAlexClientTest {

	private static final String SECRET = "test-secret-key-123";
	private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
	private static final String PAGE = """
			{"meta": {"count": 964424, "page": 2, "per_page": 2},
			 "results": [{"id": "https://openalex.org/W1"}, {"id": "https://openalex.org/W2"}]}""";

	private final List<Duration> sleeps = new ArrayList<>();
	private MockRestServiceServer server;

	private OpenAlexClient client(String apiKey, int maxAttempts) {
		return client(apiKey, maxAttempts, DataSize.ofMegabytes(8));
	}

	private OpenAlexClient client(String apiKey, int maxAttempts, DataSize maxResponse) {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openalex.test");
		server = MockRestServiceServer.bindTo(builder).build();
		OpenAlexProperties props = new OpenAlexProperties("https://api.openalex.test", apiKey, Duration.ofSeconds(1),
				Duration.ofSeconds(1), maxAttempts, Duration.ofSeconds(1), Duration.ofSeconds(30), 100, maxResponse);
		return new OpenAlexClient(builder.build(), props, JsonMapper.builder().build(), sleeps::add,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void searchSendsBearerKeyAndExpectedQuery() {
		OpenAlexClient client = client(SECRET, 3);
		server.expect(requestTo(Matchers.startsWith("https://api.openalex.test/works?")))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + SECRET))
				.andExpect(queryParam("search", "graph%20neural%20networks"))
				.andExpect(queryParam("page", "2"))
				.andExpect(queryParam("per_page", "2"))
				.andExpect(queryParam("select", Matchers.containsString("abstract_inverted_index")))
				.andExpect(request -> assertThat(request.getURI().toString())
						.doesNotContain("api_key").doesNotContain(SECRET))
				.andRespond(withSuccess(PAGE, MediaType.APPLICATION_JSON));

		OpenAlexSearchPage page = client.searchWorks("graph neural networks", 2, 2);

		server.verify();
		assertThat(page.totalCount()).isEqualTo(964424L);
		assertThat(page.page()).isEqualTo(2);
		assertThat(page.results()).hasSize(2);
		assertThat(page.results().get(0).path("id").stringValue()).isEqualTo("https://openalex.org/W1");
		assertThat(page.retrievedAt()).isEqualTo(NOW);
		assertThat(sleeps).isEmpty();
	}

	@Test
	void noAuthorizationHeaderWithoutKey() {
		OpenAlexClient client = client("  ", 3);
		server.expect(requestTo(Matchers.startsWith("https://api.openalex.test/works?")))
				.andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
				.andRespond(withSuccess(PAGE, MediaType.APPLICATION_JSON));

		client.searchWorks("x", 1, 2);
		server.verify();
	}

	@Test
	void retries429HonouringRetryAfterThenSucceeds() {
		OpenAlexClient client = client(SECRET, 3);
		server.expect(requestTo(Matchers.startsWith("https://api.openalex.test/works?")))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "7"));
		server.expect(requestTo(Matchers.startsWith("https://api.openalex.test/works?")))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		server.expect(requestTo(Matchers.startsWith("https://api.openalex.test/works?")))
				.andRespond(withSuccess(PAGE, MediaType.APPLICATION_JSON));

		OpenAlexSearchPage page = client.searchWorks("x", 1, 2);

		server.verify();
		assertThat(page.results()).hasSize(2);
		// Retry-After (7s) used first, then exponential backoff (initial 1s doubled to 2s).
		assertThat(sleeps).containsExactly(Duration.ofSeconds(7), Duration.ofSeconds(2));
	}

	@Test
	void givesUpAfterMaxAttemptsWithoutLeakingKey() {
		OpenAlexClient client = client(SECRET, 2);
		server.expect(times(2), requestTo(Matchers.startsWith("https://api.openalex.test/works?")))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

		assertThatThrownBy(() -> client.searchWorks("x", 1, 2))
				.isInstanceOfSatisfying(OpenAlexException.class, e -> {
					assertThat(e.httpStatus()).isEqualTo(429);
					assertThat(e.isRateLimited()).isTrue();
					assertThat(e.getMessage()).contains("HTTP 429").contains("2 attempt").doesNotContain(SECRET);
				});
		server.verify();
		assertThat(sleeps).hasSize(1);
	}

	@Test
	void doesNotRetryClientErrors() {
		OpenAlexClient client = client(SECRET, 3);
		server.expect(times(1), requestTo(Matchers.startsWith("https://api.openalex.test/works?")))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"error\":\"bad key " + SECRET + "\"}"));

		assertThatThrownBy(() -> client.searchWorks("x", 1, 2))
				.isInstanceOfSatisfying(OpenAlexException.class, e -> {
					assertThat(e.httpStatus()).isEqualTo(401);
					assertThat(e.getMessage()).doesNotContain(SECRET); // response body is not echoed
				});
		server.verify();
		assertThat(sleeps).isEmpty();
	}

	@Test
	void retriesNetworkErrors() {
		OpenAlexClient client = client(SECRET, 2);
		server.expect(requestTo(Matchers.startsWith("https://api.openalex.test/works?")))
				.andRespond(withException(new SocketTimeoutException("read timed out")));
		server.expect(requestTo(Matchers.startsWith("https://api.openalex.test/works?")))
				.andRespond(withSuccess(PAGE, MediaType.APPLICATION_JSON));

		assertThat(client.searchWorks("x", 1, 2).results()).hasSize(2);
		assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
	}

	@Test
	void networkFailureAfterRetriesHasNoStatus() {
		OpenAlexClient client = client(SECRET, 2);
		server.expect(times(2), requestTo(Matchers.startsWith("https://api.openalex.test/works?")))
				.andRespond(withException(new IOException("connection refused")));

		assertThatThrownBy(() -> client.searchWorks("x", 1, 2))
				.isInstanceOfSatisfying(OpenAlexException.class, e -> assertThat(e.httpStatus()).isNull());
	}

	private static final String WORKS = "https://api.openalex.test/works?";

	@Test
	void emptyResultsAreAValidPage() {
		OpenAlexClient client = client(SECRET, 3);
		server.expect(requestTo(Matchers.startsWith(WORKS)))
				.andRespond(withSuccess("{\"meta\": {\"count\": 0}, \"results\": []}", MediaType.APPLICATION_JSON));

		OpenAlexSearchPage page = client.searchWorks("zzqx nonexistent topic", 1, 10);
		assertThat(page.totalCount()).isZero();
		assertThat(page.results()).isEmpty();
	}

	@Test
	void malformedJsonFailsWithoutRetry() {
		OpenAlexClient client = client(SECRET, 3);
		server.expect(times(1), requestTo(Matchers.startsWith(WORKS)))
				.andRespond(withSuccess("{\"meta\": {\"count\": 1}, \"results\": [", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.searchWorks("x", 1, 2))
				.isInstanceOfSatisfying(OpenAlexException.class, e -> {
					assertThat(e.httpStatus()).isEqualTo(200);
					assertThat(e.getMessage()).contains("invalid JSON");
				});
		server.verify();
	}

	@Test
	void unexpectedShapeIsRejectedNotSilentlyMisread() {
		OpenAlexClient client = client(SECRET, 3);
		// "results" as an object would otherwise be iterated as if its values were works.
		server.expect(requestTo(Matchers.startsWith(WORKS)))
				.andRespond(withSuccess("{\"results\": {\"a\": {\"id\": \"https://openalex.org/W1\"}}}",
						MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> client.searchWorks("x", 1, 2))
				.isInstanceOfSatisfying(OpenAlexException.class,
						e -> assertThat(e.getMessage()).contains("unexpected response shape"));

		OpenAlexClient client2 = client(SECRET, 3);
		server.expect(requestTo(Matchers.startsWith(WORKS)))
				.andRespond(withSuccess("[{\"id\": \"https://openalex.org/W1\"}]", MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> client2.searchWorks("x", 1, 2)).isInstanceOf(OpenAlexException.class);

		OpenAlexClient client3 = client(SECRET, 3);
		server.expect(requestTo(Matchers.startsWith(WORKS)))
				.andRespond(withSuccess("{\"meta\": {\"count\": 3}}", MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> client3.searchWorks("x", 1, 2)).isInstanceOf(OpenAlexException.class);
	}

	@Test
	void forbiddenIsNotRetried() {
		OpenAlexClient client = client(SECRET, 3);
		server.expect(times(1), requestTo(Matchers.startsWith(WORKS))).andRespond(withStatus(HttpStatus.FORBIDDEN));

		assertThatThrownBy(() -> client.searchWorks("x", 1, 2))
				.isInstanceOfSatisfying(OpenAlexException.class, e -> assertThat(e.httpStatus()).isEqualTo(403));
		server.verify();
		assertThat(sleeps).isEmpty();
	}

	/** Redirects are never followed (the key must not travel to another host); a 3xx is a non-retried failure. */
	@Test
	void redirectIsAFailureNotFollowed() {
		OpenAlexClient client = client(SECRET, 3);
		server.expect(times(1), requestTo(Matchers.startsWith(WORKS))).andRespond(withStatus(HttpStatus.FOUND)
				.header("Location", "https://attacker.example/steal"));

		assertThatThrownBy(() -> client.searchWorks("x", 1, 2))
				.isInstanceOfSatisfying(OpenAlexException.class, e -> assertThat(e.httpStatus()).isEqualTo(302));
		server.verify();
		assertThat(sleeps).isEmpty();
	}

	@Test
	void oversizedBodyIsRejectedWithoutRetry() {
		OpenAlexClient client = client(SECRET, 3, DataSize.ofBytes(100));
		String big = "{\"meta\": {\"count\": 1}, \"results\": [], \"pad\": \"" + "x".repeat(200) + "\"}";
		server.expect(times(1), requestTo(Matchers.startsWith(WORKS)))
				.andRespond(withSuccess(big, MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.searchWorks("x", 1, 2))
				.isInstanceOfSatisfying(OpenAlexException.class,
						e -> assertThat(e.getMessage()).contains("exceeded 100 bytes"));
		server.verify();
		assertThat(sleeps).isEmpty();
	}

	@Test
	void declaredOversizedContentLengthIsRejectedBeforeReading() {
		OpenAlexClient client = client(SECRET, 3, DataSize.ofBytes(100));
		server.expect(requestTo(Matchers.startsWith(WORKS)))
				.andRespond(withSuccess("{\"results\": []}", MediaType.APPLICATION_JSON)
						.header(HttpHeaders.CONTENT_LENGTH, "999999999"));

		assertThatThrownBy(() -> client.searchWorks("x", 1, 2))
				.isInstanceOfSatisfying(OpenAlexException.class,
						e -> assertThat(e.getMessage()).contains("exceeded"));
	}

	@Test
	void bodyExactlyAtTheLimitIsAccepted() {
		String body = "{\"meta\": {\"count\": 0}, \"results\": []}";
		OpenAlexClient client = client(SECRET, 3, DataSize.ofBytes(body.length()));
		server.expect(requestTo(Matchers.startsWith(WORKS))).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		assertThat(client.searchWorks("x", 1, 2).results()).isEmpty();
	}

	@Test
	void topicTextCannotInjectQueryParameters() {
		OpenAlexClient client = client(SECRET, 3);
		String hostile = "gnn&per_page=1000&api_key=stolen&filter=x#frag";
		server.expect(requestTo(Matchers.startsWith(WORKS)))
				.andExpect(request -> {
					assertThat(request.getURI().getHost()).isEqualTo("api.openalex.test");
					assertThat(request.getURI().getRawQuery()).doesNotContain("api_key=").doesNotContain("filter=x");
				})
				.andExpect(queryParam("per_page", "2"))
				.andExpect(queryParam("search", Matchers.containsString("gnn")))
				.andRespond(withSuccess(PAGE, MediaType.APPLICATION_JSON));

		client.searchWorks(hostile, 1, 2);
		server.verify();
	}

	@Test
	void rejectsInvalidPaging() {
		OpenAlexClient client = client(SECRET, 3);
		assertThatThrownBy(() -> client.searchWorks("x", 1, 101)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> client.searchWorks("x", 0, 10)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void propertiesToStringHidesKey() {
		OpenAlexProperties props = new OpenAlexProperties("u", SECRET, Duration.ZERO, Duration.ZERO, 1,
				Duration.ZERO, Duration.ZERO, 100, DataSize.ofMegabytes(8));
		assertThat(props.toString()).doesNotContain(SECRET).contains("<set>");
	}

}
