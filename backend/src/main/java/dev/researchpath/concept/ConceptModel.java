package dev.researchpath.concept;

import java.util.List;
import java.util.Map;

import dev.researchpath.publication.NormalizedWork.ConceptKind;

/**
 * Query-specific coverage concepts for one snapshot (playbook M07), plus a human-readable ledger.
 *
 * @param paperConcepts publication id → included concept key → strength (provider confidence)
 * @param excludedByReason count of excluded concepts per reason
 * @param excluded the most-supported excluded concepts (bounded list, for review)
 */
public record ConceptModel(int policyVersion, Representation representation, int candidates,
		List<Entry> included, Map<String, Integer> excludedByReason, List<Entry> excluded,
		Map<Long, Map<String, Double>> paperConcepts, Stats stats) {

	public enum Representation {
		TOPIC_ONLY, KEYWORD_ONLY, COMBINED
	}

	/**
	 * One concept. {@code status} is INCLUDED or the exclusion reason; {@code weight} is 0 when excluded.
	 *
	 * @param support candidates carrying the concept at or above the confidence threshold
	 * @param samplePublications up to five supporting OpenAlex ids
	 */
	public record Entry(String key, ConceptKind kind, String label, List<String> originalLabels, int support,
			double weight, String status, List<String> samplePublications) {
	}

	/**
	 * Descriptive statistics of the representation over the candidate pool.
	 *
	 * @param topConceptShare support of the most common included concept / candidates (concentration)
	 * @param distinctSignatures number of distinct included-concept sets among candidates (discrimination)
	 * @param meanPairwiseJaccard mean Jaccard similarity of candidates' concept sets (redundancy proxy)
	 */
	public record Stats(int universeSize, int candidatesWithConcepts, double meanConceptsPerCandidate,
			double topConceptShare, int distinctSignatures, double meanPairwiseJaccard) {
	}

}
