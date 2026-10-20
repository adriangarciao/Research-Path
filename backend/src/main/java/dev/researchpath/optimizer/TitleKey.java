package dev.researchpath.optimizer;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Conservative "same displayed title" key for the one-paper-per-title constraint (D18). Only differences a
 * reader would not notice are ignored: case, Unicode composition, typographic quotes and dashes, runs of whitespace,
 * spacing around {@code : ; ,} and leading/trailing punctuation (e.g. a final period). Accents, hyphen vs space,
 * plurals, abbreviations and word order still distinguish titles; there is no fuzzy or semantic matching. Equal keys
 * never merge records; they only stop a second such work from entering the same path.
 */
public final class TitleKey {

	private static final Pattern SINGLE_QUOTES = Pattern.compile("[‘’‚‛′]");
	private static final Pattern DOUBLE_QUOTES = Pattern.compile("[“”„‟″]");
	private static final Pattern DASHES = Pattern.compile("[‐‑‒–—―−]");
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");
	private static final Pattern SEPARATOR_SPACING = Pattern.compile("\\s*([:;,])\\s*");
	private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[\\p{P}\\s]+|[\\p{P}\\s]+$");

	private TitleKey() {
	}

	/** The key, or null for a missing or punctuation-only title (such works are never constrained). */
	public static String of(String title) {
		if (title == null) {
			return null;
		}
		String s = Normalizer.normalize(title, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
		s = SINGLE_QUOTES.matcher(s).replaceAll("'");
		s = DOUBLE_QUOTES.matcher(s).replaceAll("\"");
		s = DASHES.matcher(s).replaceAll("-");
		s = WHITESPACE.matcher(s).replaceAll(" ");
		s = SEPARATOR_SPACING.matcher(s).replaceAll("$1 ");
		s = EDGE_PUNCTUATION.matcher(s).replaceAll("");
		return s.isEmpty() ? null : s;
	}

	/** Variant reported in the M4 evaluation only: like {@link #of} but hyphens count as spaces. */
	public static String hyphenInsensitive(String title) {
		String key = of(title);
		if (key == null) {
			return null;
		}
		String s = WHITESPACE.matcher(key.replace('-', ' ')).replaceAll(" ").trim();
		return s.isEmpty() ? null : s;
	}

}
