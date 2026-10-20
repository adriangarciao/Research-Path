package dev.researchpath.openalex;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * The overall time allowed for every provider call of one generation, pages and retries included (release decision
 * R5, M7). Created once per generation and passed to each {@link OpenAlexClient#searchWorks} call.
 */
public record ProviderDeadline(Instant expiresAt) {

	/** No overall limit (per-attempt timeouts still apply). */
	public static final ProviderDeadline NONE = new ProviderDeadline(Instant.MAX);

	public static ProviderDeadline after(Clock clock, Duration budget) {
		return new ProviderDeadline(clock.instant().plus(budget));
	}

	Duration remaining(Clock clock) {
		return Duration.between(clock.instant(), expiresAt);
	}

}
