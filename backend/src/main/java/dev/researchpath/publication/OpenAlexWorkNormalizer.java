package dev.researchpath.publication;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import dev.researchpath.publication.NormalizationResult.Accepted;
import dev.researchpath.publication.NormalizationResult.Rejected;
import dev.researchpath.publication.NormalizationResult.RejectionReason;
import dev.researchpath.publication.NormalizedWork.Author;
import dev.researchpath.publication.NormalizedWork.ConceptAssociation;
import dev.researchpath.publication.NormalizedWork.ConceptKind;
import tools.jackson.databind.JsonNode;

/**
 * Converts one OpenAlex work record into a {@link NormalizedWork}.
 *
 * <p>Rules: reject only records that cannot be identified (no valid work id) or displayed
 * (no title). Every other missing field becomes null / empty and is reflected in the
 * availability flags. Nothing is inferred or invented. Unsafe or malformed URLs are dropped
 * (see {@link UrlSafety}); the original value remains in the snapshot's raw provider record.
 */
@Component
public class OpenAlexWorkNormalizer {

	/** Bump when normalization output changes, so snapshots record which rules produced them. */
	public static final int VERSION = 2;

	/** Resource bounds (playbook M04). Measured on 420 real records: largest 41 KB, average 11.6 KB. */
	public static final int MAX_RECORD_BYTES = 512 * 1024;
	public static final int MAX_TITLE_CHARS = 1_000;
	public static final int MAX_ABSTRACT_CHARS = 20_000;
	public static final int MAX_STORED_AUTHORS = 100;

	public NormalizationResult normalize(JsonNode work) {
		String rawId = text(work.path("id"));
		String openalexId = TextNormalization.workId(rawId);
		if (openalexId == null) {
			return new Rejected(rawId, RejectionReason.MISSING_OR_INVALID_ID);
		}
		if (work.toString().getBytes(StandardCharsets.UTF_8).length > MAX_RECORD_BYTES) {
			return new Rejected(openalexId, RejectionReason.RECORD_TOO_LARGE);
		}
		String title = firstNonBlank(text(work.path("display_name")), text(work.path("title")));
		if (title == null) {
			return new Rejected(openalexId, RejectionReason.MISSING_TITLE);
		}
		if (title.length() > MAX_TITLE_CHARS) {
			return new Rejected(openalexId, RejectionReason.TITLE_TOO_LONG);
		}
		String abstractText = abstractText(work.path("abstract_inverted_index"));
		boolean abstractTruncated = abstractText != null && abstractText.length() > MAX_ABSTRACT_CHARS;
		if (abstractTruncated) {
			abstractText = truncateAtWord(abstractText, MAX_ABSTRACT_CHARS);
		}
		List<Author> allAuthors = authors(work.path("authorships"));
		JsonNode references = work.path("referenced_works");

		JsonNode primaryLocation = work.path("primary_location");
		JsonNode openAccess = work.path("open_access");

		return new Accepted(new NormalizedWork(
				openalexId,
				TextNormalization.doi(firstNonBlank(text(work.path("doi")), text(work.path("ids").path("doi")))),
				title,
				TextNormalization.title(title),
				integer(work.path("publication_year")),
				date(text(work.path("publication_date"))),
				text(work.path("type")),
				text(work.path("language")),
				text(primaryLocation.path("source").path("display_name")),
				UrlSafety.safeDisplayUrl(text(primaryLocation.path("landing_page_url"))),
				UrlSafety.safeDisplayUrl(text(openAccess.path("oa_url"))),
				bool(openAccess.path("is_oa")),
				abstractText,
				integer(work.path("cited_by_count")),
				decimal(work.path("fwci")),
				decimal(work.path("citation_normalized_percentile").path("value")),
				integer(work.path("referenced_works_count")),
				instant(text(work.path("updated_date"))),
				allAuthors.subList(0, Math.min(allAuthors.size(), MAX_STORED_AUTHORS)),
				concepts(work),
				referencedWorks(references),
				allAuthors.size(),
				abstractTruncated,
				references.isArray()));
	}

	/** Rebuilds abstract text from OpenAlex's {word: [positions]} inverted index. */
	static String abstractText(JsonNode invertedIndex) {
		if (!invertedIndex.isObject() || invertedIndex.isEmpty()) {
			return null;
		}
		Map<Integer, String> byPosition = new TreeMap<>();
		for (Map.Entry<String, JsonNode> entry : invertedIndex.properties()) {
			for (JsonNode position : entry.getValue()) {
				if (position.isIntegralNumber()) {
					byPosition.put(position.intValue(), entry.getKey());
				}
			}
		}
		String text = String.join(" ", byPosition.values()).trim();
		return text.isEmpty() ? null : text;
	}

	private static List<Author> authors(JsonNode authorships) {
		List<Author> authors = new ArrayList<>();
		for (JsonNode authorship : authorships) {
			JsonNode author = authorship.path("author");
			String name = firstNonBlank(text(author.path("display_name")), text(authorship.path("raw_author_name")));
			if (name == null) {
				continue; // do not fabricate an author name
			}
			authors.add(new Author(authors.size() + 1, TextNormalization.shortOpenAlexId(text(author.path("id"))),
					name, text(authorship.path("author_position"))));
		}
		return authors;
	}

	private static List<ConceptAssociation> concepts(JsonNode work) {
		List<ConceptAssociation> concepts = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		String primaryTopicId = TextNormalization.shortOpenAlexId(text(work.path("primary_topic").path("id")));
		for (JsonNode topic : work.path("topics")) {
			String id = TextNormalization.shortOpenAlexId(text(topic.path("id")));
			String name = text(topic.path("display_name"));
			if (id == null || name == null || !seen.add("T:" + id)) {
				continue;
			}
			concepts.add(new ConceptAssociation(ConceptKind.TOPIC, id, name, confidence(topic.path("score")),
					id.equals(primaryTopicId), text(topic.path("subfield").path("display_name")),
					text(topic.path("field").path("display_name")), text(topic.path("domain").path("display_name"))));
		}
		for (JsonNode keyword : work.path("keywords")) {
			String id = TextNormalization.shortOpenAlexId(text(keyword.path("id")));
			String name = text(keyword.path("display_name"));
			if (id == null || name == null || !seen.add("K:" + id)) {
				continue;
			}
			concepts.add(new ConceptAssociation(ConceptKind.KEYWORD, id, name, confidence(keyword.path("score")),
					false, null, null, null));
		}
		return concepts;
	}

	private static List<String> referencedWorks(JsonNode references) {
		Set<String> ids = new LinkedHashSet<>();
		for (JsonNode reference : references) {
			String id = TextNormalization.workId(text(reference));
			if (id != null) {
				ids.add(id);
			}
		}
		return List.copyOf(ids);
	}

	// ---- null-safe JSON accessors: missing, null or wrongly typed values all become null ----

	private static String text(JsonNode node) {
		if (node == null || !node.isString()) {
			return null;
		}
		String value = node.stringValue().trim();
		return value.isEmpty() ? null : value;
	}

	private static Integer integer(JsonNode node) {
		return node != null && node.isIntegralNumber() ? node.intValue() : null;
	}

	private static Double decimal(JsonNode node) {
		return node != null && node.isNumber() ? node.doubleValue() : null;
	}

	/** Provider confidence must be a finite number in [0, 1]; anything else is unknown (null). */
	private static Double confidence(JsonNode node) {
		Double value = decimal(node);
		return value != null && Double.isFinite(value) && value >= 0.0 && value <= 1.0 ? value : null;
	}

	/** Cuts at the last space at or before {@code max} characters (hard cut if there is none). */
	static String truncateAtWord(String text, int max) {
		if (text.length() <= max) {
			return text;
		}
		int cut = text.lastIndexOf(' ', max);
		return (cut > 0 ? text.substring(0, cut) : text.substring(0, max)).stripTrailing();
	}

	private static Boolean bool(JsonNode node) {
		return node != null && node.isBoolean() ? node.booleanValue() : null;
	}

	private static LocalDate date(String value) {
		if (value == null) {
			return null;
		}
		try {
			return LocalDate.parse(value);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	/** OpenAlex timestamps carry no zone; they are UTC. Accepts a plain date too. */
	private static Instant instant(String value) {
		if (value == null) {
			return null;
		}
		try {
			return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
		} catch (DateTimeParseException e) {
			LocalDate day = date(value);
			return day == null ? null : day.atStartOfDay().toInstant(ZoneOffset.UTC);
		}
	}

	private static String firstNonBlank(String a, String b) {
		return a != null ? a : b;
	}

}
