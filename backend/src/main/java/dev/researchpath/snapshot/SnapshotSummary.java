package dev.researchpath.snapshot;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/**
 * Descriptive report for one candidate snapshot, mirroring the metrics of the October 2026
 * audit. Counts describe this retrieved pool only; they are not quality or relevance judgments.
 *
 * @param internalCitationEdges references between members of this snapshot (uses the current
 *     publication cache, so it can drift from snapshot time if a member was refreshed later)
 */
public record SnapshotSummary(
		UUID id,
		String topic,
		Instant createdAt,
		String provider,
		int requestedCount,
		Long providerTotalCount,
		int receivedCount,
		int storedCount,
		int rejectedCount,
		int normalizerVersion,
		Availability availability,
		Classification classification,
		long internalCitationEdges,
		long possibleDuplicatePairs,
		JsonNode retrievalNotes) {

	/**
	 * Number of members with each kind of metadata present. {@code withReferences} counts non-empty
	 * reference lists; {@code withReferenceListSupplied} counts lists supplied at all (possibly empty).
	 * {@code withSourceLink} counts members with a safe preferred link (open access, DOI or landing page).
	 */
	public record Availability(int withAbstract, int withTopics, int withKeywords, int withReferences, int withDoi,
			int withYear, int withAuthors, int withSourceLink, int withReferenceListSupplied, int abstractsTruncated) {
	}

	/**
	 * Distinct labels across members, with no confidence threshold (thresholds are OPEN).
	 * {@code mostCommonPrimaryTopic} shows topic concentration (blueprint decision D02).
	 */
	public record Classification(int distinctTopics, int distinctKeywords, String mostCommonPrimaryTopic,
			int mostCommonPrimaryTopicCount) {
	}

}
