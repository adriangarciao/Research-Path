package dev.researchpath.openalex;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Daily OpenAlex call budget ({@code researchpath.provider-budget.*}; release decision R4). Counted per UTC day,
 * matching the provider's midnight-UTC reset.
 *
 * @param dailyCalls calls allowed per UTC day, retries included (a free key is $1/day = 1000 searches; the default
 *     leaves 10% headroom for manual checks)
 */
@ConfigurationProperties("researchpath.provider-budget")
public record ProviderBudgetProperties(@DefaultValue("true") boolean enabled, @DefaultValue("900") int dailyCalls) {

	public ProviderBudgetProperties {
		if (dailyCalls < 0) {
			throw new IllegalArgumentException("dailyCalls must be >= 0");
		}
	}

}
