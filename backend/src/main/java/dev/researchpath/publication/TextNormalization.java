package dev.researchpath.publication;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Deterministic string normalization for identifiers and duplicate-candidate matching. */
public final class TextNormalization {

	private static final String OPENALEX_PREFIX = "https://openalex.org/";
	private static final Pattern DOI_PREFIX = Pattern.compile("^(https?://(dx\\.)?doi\\.org/|doi:)", Pattern.CASE_INSENSITIVE);
	private static final Pattern WORK_ID = Pattern.compile("^W\\d+$");
	private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
	private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");

	private TextNormalization() {
	}

	/** "https://openalex.org/W123" -> "W123"; "https://openalex.org/keywords/x" -> "keywords/x". */
	public static String shortOpenAlexId(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		String trimmed = id.trim();
		return trimmed.startsWith(OPENALEX_PREFIX) ? trimmed.substring(OPENALEX_PREFIX.length()) : trimmed;
	}

	/** Returns the short work id (e.g. W123) or null if the value is not a valid OpenAlex work id. */
	public static String workId(String id) {
		String shortId = shortOpenAlexId(id);
		return shortId != null && WORK_ID.matcher(shortId).matches() ? shortId : null;
	}

	/** Lowercase DOI without resolver prefix, e.g. "10.1109/tnn.2008.2005605". */
	public static String doi(String doi) {
		if (doi == null || doi.isBlank()) {
			return null;
		}
		String bare = DOI_PREFIX.matcher(doi.trim()).replaceFirst("");
		return bare.isEmpty() ? null : bare.toLowerCase(Locale.ROOT);
	}

	/**
	 * Title key for possible-duplicate detection: case-, accent- and punctuation-insensitive.
	 * Equal keys are a review signal, not proof of the same work (blueprint §4.3).
	 */
	public static String title(String title) {
		if (title == null) {
			return "";
		}
		String decomposed = Normalizer.normalize(title, Normalizer.Form.NFKD);
		String noMarks = DIACRITICS.matcher(decomposed).replaceAll("");
		return NON_ALNUM.matcher(noMarks.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
	}

	/** Topic text as typed, with whitespace collapsed and case folded (for snapshot lookup). */
	public static String topic(String topic) {
		return topic == null ? "" : topic.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

}
