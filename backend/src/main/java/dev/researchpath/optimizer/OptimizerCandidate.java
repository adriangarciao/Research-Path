package dev.researchpath.optimizer;

import java.util.Map;

/**
 * Optimizer input for one eligible, non-duplicate candidate. Feature values are in [0, 1] or null when
 * unknown; the optimizer treats unknown as neutral, never as zero or best.
 *
 * @param id stable identifier (OpenAlex work id)
 * @param providerRank tie-break only
 * @param concepts concept key → strength (provider confidence)
 * @param relevance relevance-gate evidence in [0, 1]
 * @param influence age/field-normalized citation percentile in [0, 1], null if unknown
 * @param year publication year, null if unknown
 * @param overviewLike title/type marks a survey, review, tutorial or introduction (weak accessibility cue)
 */
public record OptimizerCandidate(String id, int providerRank, Map<String, Double> concepts, double relevance,
		Double influence, Integer year, boolean overviewLike) {

	public OptimizerCandidate {
		concepts = Map.copyOf(concepts);
	}

}
