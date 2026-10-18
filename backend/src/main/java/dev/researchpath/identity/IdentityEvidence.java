package dev.researchpath.identity;

import java.util.Set;

/**
 * What the identity policy may look at for one publication. Built from the publication cache.
 *
 * @param authorIds OpenAlex author ids (may be empty)
 * @param authorNameKeys surname + first initial keys, see {@link IdentityPolicy#nameKey(String)}
 * @param authorCount number of named authors (0 = no author data)
 * @param metadataCompleteness count of available fields used for canonical choice
 */
public record IdentityEvidence(
		long publicationId,
		String openalexId,
		String doi,
		String normalizedTitle,
		Integer year,
		String workType,
		Set<String> authorIds,
		Set<String> authorNameKeys,
		int authorCount,
		boolean hasOpenAccessLink,
		boolean hasSourceLink,
		boolean hasAbstract,
		int metadataCompleteness) {

	public IdentityEvidence {
		authorIds = Set.copyOf(authorIds);
		authorNameKeys = Set.copyOf(authorNameKeys);
	}

}
