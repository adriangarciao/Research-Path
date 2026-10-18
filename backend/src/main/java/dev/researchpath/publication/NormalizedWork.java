package dev.researchpath.publication;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Provider-independent view of one academic work. Missing values are null (unknown),
 * never invented defaults.
 */
public record NormalizedWork(
		String openalexId,
		String doi,
		String title,
		String normalizedTitle,
		Integer publicationYear,
		LocalDate publicationDate,
		String workType,
		String language,
		String sourceName,
		String landingPageUrl,
		String oaUrl,
		Boolean isOa,
		String abstractText,
		Integer citedByCount,
		Double fwci,
		Double citationPercentile,
		Integer referencedWorksCount,
		Instant providerUpdatedAt,
		List<Author> authors,
		List<ConceptAssociation> concepts,
		List<String> referencedWorkIds) {

	public NormalizedWork {
		authors = List.copyOf(authors);
		concepts = List.copyOf(concepts);
		referencedWorkIds = List.copyOf(referencedWorkIds);
	}

	public boolean hasAbstract() {
		return abstractText != null && !abstractText.isBlank();
	}

	public boolean hasTopics() {
		return concepts.stream().anyMatch(c -> c.kind() == ConceptKind.TOPIC);
	}

	public boolean hasKeywords() {
		return concepts.stream().anyMatch(c -> c.kind() == ConceptKind.KEYWORD);
	}

	public boolean hasReferences() {
		return !referencedWorkIds.isEmpty();
	}

	public record Author(int order, String openalexId, String displayName, String authorPosition) {
	}

	/** A topic or keyword with the provider's confidence score (null when not supplied). */
	public record ConceptAssociation(
			ConceptKind kind,
			String openalexId,
			String displayName,
			Double score,
			boolean primary,
			String subfield,
			String field,
			String domain) {
	}

	public enum ConceptKind {
		TOPIC, KEYWORD
	}

}
