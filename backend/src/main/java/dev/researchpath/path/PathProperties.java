package dev.researchpath.path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bounds on path generation ({@code researchpath.paths.*}). PROVISIONAL (S02): sized for local use; rate limits
 * and anonymous quotas are M13.
 *
 * @param maxConcurrentGenerations generations (new paths or regenerations) running at once; more are refused (503)
 * @param maxRevisionsPerPath revisions one path may accumulate; more are refused (409)
 */
@ConfigurationProperties("researchpath.paths")
public record PathProperties(
		@DefaultValue("4") int maxConcurrentGenerations,
		@DefaultValue("50") int maxRevisionsPerPath) {
}
