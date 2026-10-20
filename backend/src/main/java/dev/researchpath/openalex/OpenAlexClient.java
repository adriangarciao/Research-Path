package dev.researchpath.openalex;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Minimal OpenAlex works client: search with bounded retries.
 *
 * <p>Retries 429, 5xx and network errors up to {@code maxAttempts}, honouring Retry-After
 * (capped). Other 4xx responses fail immediately. A {@link ProviderDeadline} bounds all attempts of one generation:
 * an attempt (or retry wait) starts only if it can finish before the deadline, assuming the worst-case attempt time
 * {@code connectTimeout + readTimeout}, so the total never exceeds the budget. Response bodies are size-bounded before
 * parsing and must have the expected shape (object with a {@code results} array). The API key travels only in the
 * Authorization header, so it cannot appear in URLs, logs or exception messages.
 */
public class OpenAlexClient {

	/** Fields needed by normalization and snapshots (blueprint §4.2). */
	public static final String WORK_FIELDS = String.join(",", "id", "doi", "ids", "display_name", "title",
			"publication_year", "publication_date", "type", "language", "authorships", "primary_location",
			"open_access", "abstract_inverted_index", "topics", "primary_topic", "keywords", "referenced_works",
			"referenced_works_count", "cited_by_count", "fwci", "citation_normalized_percentile", "updated_date",
			"relevance_score");

	private static final Logger log = LoggerFactory.getLogger(OpenAlexClient.class);

	private final RestClient restClient;
	private final OpenAlexProperties properties;
	private final JsonMapper jsonMapper;
	private final Sleeper sleeper;
	private final Clock clock;
	private final CallBudget budget;

	@FunctionalInterface
	public interface Sleeper {
		void sleep(Duration duration) throws InterruptedException;
	}

	public OpenAlexClient(RestClient restClient, OpenAlexProperties properties, JsonMapper jsonMapper,
			Sleeper sleeper, Clock clock) {
		this(restClient, properties, jsonMapper, sleeper, clock, CallBudget.UNLIMITED);
	}

	/** @param budget daily provider call budget; one call is reserved before every attempt, retries included */
	public OpenAlexClient(RestClient restClient, OpenAlexProperties properties, JsonMapper jsonMapper,
			Sleeper sleeper, Clock clock, CallBudget budget) {
		this.budget = budget;
		this.restClient = restClient;
		this.properties = properties;
		this.jsonMapper = jsonMapper;
		this.sleeper = sleeper;
		this.clock = clock;
	}

	/** {@link #searchWorks(String, int, int, ProviderDeadline)} without an overall deadline. */
	public OpenAlexSearchPage searchWorks(String query, int page, int perPage) {
		return searchWorks(query, page, perPage, ProviderDeadline.NONE);
	}

	/**
	 * Full-text search over works (OpenAlex {@code search=} parameter), default relevance order.
	 *
	 * @param page 1-based page number
	 * @param deadline shared by every page and retry of one generation
	 */
	public OpenAlexSearchPage searchWorks(String query, int page, int perPage, ProviderDeadline deadline) {
		if (perPage < 1 || perPage > properties.maxPerPage()) {
			throw new IllegalArgumentException("perPage must be between 1 and " + properties.maxPerPage());
		}
		if (page < 1) {
			throw new IllegalArgumentException("page must be >= 1");
		}
		JsonNode body = getWithRetry(uri -> uri.path("/works")
				.queryParam("search", query)
				.queryParam("page", page)
				.queryParam("per_page", perPage)
				.queryParam("select", WORK_FIELDS)
				.build(), "search page " + page, deadline);

		JsonNode resultsNode = body.path("results");
		if (!body.isObject() || !resultsNode.isArray()) {
			throw new OpenAlexException("OpenAlex search page " + page + " returned an unexpected response shape",
					200, null);
		}
		JsonNode count = body.path("meta").path("count");
		List<JsonNode> results = new ArrayList<>();
		resultsNode.forEach(results::add);
		return new OpenAlexSearchPage(count.isIntegralNumber() ? count.longValue() : null, page, results,
				clock.instant());
	}

	private JsonNode getWithRetry(Function<UriBuilder, URI> uri, String description, ProviderDeadline deadline) {
		Duration backoff = properties.initialBackoff();
		for (int attempt = 1;; attempt++) {
			requireTime(deadline, Duration.ZERO, description);
			if (!budget.tryReserve()) {
				// Nothing is sent once today's budget is used up (also between retries).
				throw new ProviderBudgetExhaustedException(budget.secondsUntilReset());
			}
			Response response;
			try {
				response = restClient.get()
						.uri(uri)
						.headers(this::authenticate)
						.exchange((request, res) -> readBounded(res.getStatusCode().value(),
								res.getHeaders().getFirst(HttpHeaders.RETRY_AFTER), res.getHeaders().getContentLength(),
								res.getBody()), false);
			} catch (ResourceAccessException e) {
				if (neverConnected(e)) {
					budget.refund(); // the request never reached the provider, so it cannot have been billed
				}
				if (attempt >= properties.maxAttempts()) {
					throw new OpenAlexException("OpenAlex " + description + " failed: network error after "
							+ attempt + " attempt(s)", null, e);
				}
				log.warn("OpenAlex {} network error (attempt {}/{}), retrying", description, attempt,
						properties.maxAttempts());
				requireTime(deadline, backoff, description);
				pause(backoff);
				backoff = next(backoff);
				continue;
			}

			if (response.tooLarge()) {
				// Not retried: the provider would return the same oversized body again.
				throw new OpenAlexException("OpenAlex " + description + " response exceeded "
						+ properties.maxResponseSize().toBytes() + " bytes", response.status(), null);
			}
			if (response.status() == 200) {
				try {
					return jsonMapper.readTree(response.body());
				} catch (JacksonException e) {
					throw new OpenAlexException("OpenAlex " + description + " returned invalid JSON", 200, e);
				}
			}
			boolean retryable = response.status() == 429 || response.status() >= 500;
			if (!retryable || attempt >= properties.maxAttempts()) {
				throw new OpenAlexException("OpenAlex " + description + " failed: HTTP " + response.status()
						+ " after " + attempt + " attempt(s)", response.status(), null);
			}
			Duration wait = retryAfter(response.retryAfter()).orElse(backoff);
			log.warn("OpenAlex {} HTTP {} (attempt {}/{}), retrying in {} ms", description, response.status(),
					attempt, properties.maxAttempts(), wait.toMillis());
			requireTime(deadline, wait, description);
			pause(wait);
			backoff = next(backoff);
		}
	}

	/** Fails fast, before waiting or sending, unless {@code wait} plus a worst-case attempt fits before the deadline. */
	private void requireTime(ProviderDeadline deadline, Duration wait, String description) {
		Duration needed = wait.plus(attemptLimit());
		if (deadline.remaining(clock).compareTo(needed) < 0) {
			throw new ProviderTimeBudgetExceededException("OpenAlex " + description
					+ " stopped: the overall provider time budget is too short for another attempt");
		}
	}

	/** Longest one attempt can take: connecting plus the read timeout (which also bounds reading the body). */
	Duration attemptLimit() {
		return properties.connectTimeout().plus(properties.readTimeout());
	}

	/** Failures before a connection existed; a read timeout is not one of them (the request may have been billed). */
	static boolean neverConnected(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof java.net.ConnectException || t instanceof java.net.UnknownHostException
					|| t instanceof java.net.http.HttpConnectTimeoutException) {
				return true;
			}
		}
		return false;
	}

	private void authenticate(HttpHeaders headers) {
		if (properties.hasApiKey()) {
			headers.setBearerAuth(properties.apiKey().trim());
		}
	}

	private Optional<Duration> retryAfter(String header) {
		if (header == null) {
			return Optional.empty();
		}
		try {
			long seconds = Long.parseLong(header.trim());
			return Optional.of(min(Duration.ofSeconds(Math.max(0, seconds)), properties.maxBackoff()));
		} catch (NumberFormatException e) {
			return Optional.empty(); // HTTP-date form: fall back to exponential backoff
		}
	}

	private Duration next(Duration current) {
		return min(current.multipliedBy(2), properties.maxBackoff());
	}

	private static Duration min(Duration a, Duration b) {
		return a.compareTo(b) <= 0 ? a : b;
	}

	private void pause(Duration duration) {
		try {
			sleeper.sleep(duration);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new OpenAlexException("Interrupted while waiting to retry OpenAlex", null, e);
		}
	}

	/**
	 * Reads at most {@code maxResponseSize} bytes. A declared Content-Length over the limit is
	 * rejected before reading; otherwise one extra byte is read to detect an oversized body.
	 */
	private Response readBounded(int status, String retryAfter, long declaredLength, InputStream body)
			throws IOException {
		long max = properties.maxResponseSize().toBytes();
		if (declaredLength > max) {
			return new Response(status, retryAfter, new byte[0], true);
		}
		byte[] bytes = body.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, max + 1));
		return bytes.length > max ? new Response(status, retryAfter, new byte[0], true)
				: new Response(status, retryAfter, bytes, false);
	}

	private record Response(int status, String retryAfter, byte[] body, boolean tooLarge) {
	}

}
