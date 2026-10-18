package dev.researchpath.openalex;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * OpenAlex connection settings ({@code researchpath.openalex.*}).
 *
 * @param apiKey server-side only; may be blank (keyless access has a much smaller budget)
 * @param maxPerPage provider page-size limit
 * @param maxResponseSize largest response body read before parsing (larger responses are rejected)
 */
@ConfigurationProperties("researchpath.openalex")
public record OpenAlexProperties(
		@DefaultValue("https://api.openalex.org") String baseUrl,
		String apiKey,
		@DefaultValue("5s") Duration connectTimeout,
		@DefaultValue("30s") Duration readTimeout,
		@DefaultValue("3") int maxAttempts,
		@DefaultValue("1s") Duration initialBackoff,
		@DefaultValue("30s") Duration maxBackoff,
		@DefaultValue("100") int maxPerPage,
		@DefaultValue("8MB") DataSize maxResponseSize) {

	public boolean hasApiKey() {
		return apiKey != null && !apiKey.isBlank();
	}

	/** Never expose the key through logging or error messages. */
	@Override
	public String toString() {
		return "OpenAlexProperties[baseUrl=" + baseUrl + ", apiKey=" + (hasApiKey() ? "<set>" : "<none>")
				+ ", maxAttempts=" + maxAttempts + ", maxPerPage=" + maxPerPage + "]";
	}

}
