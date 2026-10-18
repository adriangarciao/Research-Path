package dev.researchpath.identity;

import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/**
 * Human-inspectable view of a snapshot's frozen identity resolution (playbook M05: expose decisions).
 *
 * @param policyVersion null when the snapshot predates identity resolution (nothing evaluated)
 */
public record IdentityDiagnostics(UUID snapshotId, Integer policyVersion, List<Decision> decisions,
		List<Suppressed> suppressed) {

	public record Work(String openalexId, String title, Integer year, String workType, String doi) {
	}

	public record Decision(long pairId, String matchType, String decision, String rule, JsonNode details, Work a,
			Work b) {
	}

	public record Suppressed(String openalexId, String title, String canonicalOpenalexId, String canonicalTitle) {
	}

}
