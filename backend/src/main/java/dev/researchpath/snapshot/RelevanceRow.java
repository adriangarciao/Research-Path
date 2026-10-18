package dev.researchpath.snapshot;

import tools.jackson.databind.JsonNode;

/** One member's frozen relevance decision, for diagnostics. {@code tier} is null for pre-V5 snapshots. */
public record RelevanceRow(int providerRank, String openalexId, String title, String tier, String reason,
		boolean optimizationCandidate, JsonNode evidence) {
}
