package dev.researchpath.snapshot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Candidate retrieval bounds ({@code researchpath.ingestion.*}).
 *
 * <p>The ideal pool size is PROVISIONAL (blueprint decision D11: 100-300 is a planning target,
 * not a tested value). These are configuration, not conclusions.
 */
@ConfigurationProperties("researchpath.ingestion")
public record IngestionProperties(
		@DefaultValue("100") int defaultCandidates,
		@DefaultValue("200") int maxCandidates,
		@DefaultValue("300") int maxTopicLength) {
}
