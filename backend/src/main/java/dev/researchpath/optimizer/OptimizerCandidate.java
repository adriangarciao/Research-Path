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
 * @param citations raw citation count, null if unknown (only compared within the pool: landmark signal, D16)
 * @param titleKey {@link TitleKey} of the displayed title, null if none; at most one paper per key when the
 *     configuration asks for distinct titles (D18)
 */
public record OptimizerCandidate(String id, int providerRank, Map<String, Double> concepts, double relevance,
		Double influence, Integer year, boolean overviewLike, Integer citations, String titleKey) {

	public OptimizerCandidate {
		concepts = Map.copyOf(concepts);
	}

	/** Without a title key (never constrained by the one-paper-per-title rule). */
	public OptimizerCandidate(String id, int providerRank, Map<String, Double> concepts, double relevance,
			Double influence, Integer year, boolean overviewLike, Integer citations) {
		this(id, providerRank, concepts, relevance, influence, year, overviewLike, citations, null);
	}

	/** Without a citation count (the landmark signal treats it as neutral). */
	public OptimizerCandidate(String id, int providerRank, Map<String, Double> concepts, double relevance,
			Double influence, Integer year, boolean overviewLike) {
		this(id, providerRank, concepts, relevance, influence, year, overviewLike, null, null);
	}

}
