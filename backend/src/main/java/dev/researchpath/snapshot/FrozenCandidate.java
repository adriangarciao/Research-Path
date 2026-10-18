package dev.researchpath.snapshot;

import dev.researchpath.publication.NormalizedWork;
import tools.jackson.databind.JsonNode;

/**
 * One optimization candidate read from a snapshot's frozen inputs (never from the refreshable cache).
 *
 * @param work normalized work exactly as evaluated when the snapshot was created
 * @param relevanceEvidence the relevance gate's recorded evidence for this member
 */
public record FrozenCandidate(long publicationId, int providerRank, NormalizedWork work, JsonNode relevanceEvidence) {
}
