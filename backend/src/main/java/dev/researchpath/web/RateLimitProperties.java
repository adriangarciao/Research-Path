package dev.researchpath.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Abuse and cost limits for the public path API ({@code researchpath.rate-limit.*}; decision S02, PROVISIONAL).
 * A new path costs provider requests; a regeneration only CPU and database work, so they are limited separately.
 * Clients are identified by the connection's remote address: behind a reverse proxy, configure
 * {@code server.forward-headers-strategy} so this is the real client, never a client-supplied header.
 *
 * @param createsPerDay global cap on new paths per UTC day (bounds provider spend)
 * @param maxClients clients tracked at once (memory bound)
 */
@ConfigurationProperties("researchpath.rate-limit")
public record RateLimitProperties(
		@DefaultValue("true") boolean enabled,
		@DefaultValue("3") int createBurst,
		@DefaultValue("10") int createsPerHour,
		@DefaultValue("10") int regenerateBurst,
		@DefaultValue("120") int regenerationsPerHour,
		@DefaultValue("500") int createsPerDay,
		@DefaultValue("10000") int maxClients) {

	public static RateLimitProperties defaults() {
		return new RateLimitProperties(true, 3, 10, 10, 120, 500, 10000);
	}

}
