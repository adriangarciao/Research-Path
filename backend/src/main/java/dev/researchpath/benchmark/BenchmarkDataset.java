package dev.researchpath.benchmark;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.researchpath.publication.NormalizedWork.ConceptAssociation;

/**
 * A frozen candidate pool exported from one snapshot, so baselines and the optimizer can be benchmarked
 * offline and reproduced on another machine (playbook M08). Contains only eligible, non-duplicate
 * candidates and the fields selection, metrics and ordering need; no abstracts, no credentials.
 *
 * <p>OpenAlex metadata is CC0; datasets keep the OpenAlex work ids for attribution.
 */
public record BenchmarkDataset(
		int formatVersion,
		UUID snapshotId,
		String topic,
		Instant snapshotCreatedAt,
		Policies policies,
		List<Candidate> candidates) {

	public static final int FORMAT_VERSION = 1;

	public BenchmarkDataset {
		candidates = List.copyOf(candidates);
	}

	/** Versions of the rules that produced the snapshot's frozen inputs. */
	public record Policies(int normalizerVersion, Integer identityPolicyVersion, Integer relevancePolicyVersion) {
	}

	/**
	 * One optimization candidate.
	 *
	 * @param relevanceCoverage fraction of query terms in title or confident concepts (relevance gate evidence)
	 * @param titleTermShare fraction of query terms in the title alone
	 * @param citedByCount null when unknown (never coerced to zero)
	 * @param sourceLink preferred safe source link, null if none
	 * @param sourceLinkKind OPEN_ACCESS, DOI or LANDING_PAGE, null if no link
	 */
	public record Candidate(
			String openalexId,
			int providerRank,
			String title,
			Integer publicationYear,
			String workType,
			String doi,
			Integer citedByCount,
			Double fwci,
			Double citationPercentile,
			String sourceLink,
			String sourceLinkKind,
			double relevanceCoverage,
			double titleTermShare,
			List<ConceptAssociation> concepts,
			List<String> referencedWorkIds) {

		public Candidate {
			concepts = List.copyOf(concepts);
			referencedWorkIds = List.copyOf(referencedWorkIds);
		}
	}

}
