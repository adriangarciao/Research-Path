package dev.researchpath.publication;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Validates provider-supplied URLs before they are stored or displayed (playbook §4.3).
 *
 * <p>URLs are display-only: the application never fetches them. Accepted: absolute {@code https}
 * or {@code http} URLs with a host and no embedded credentials, at most {@value #MAX_LENGTH}
 * characters, without whitespace or control characters. {@code http} is accepted because some
 * repositories referenced by OpenAlex only serve plain HTTP; the UI must still prefer and label links.
 * Everything else ({@code javascript:}, {@code data:}, {@code file:}, relative, malformed) is rejected.
 */
public final class UrlSafety {

	public static final int MAX_LENGTH = 2048;

	private UrlSafety() {
	}

	/** Returns the trimmed (interior spaces as %20) URL if it is safe to store and display, otherwise null. */
	public static String safeDisplayUrl(String raw) {
		if (raw == null) {
			return null;
		}
		// Interior spaces occur in real repository file URLs (e.g. "preview 10.pdf"); %20 is the
		// lossless form a browser would request. Other whitespace/control characters stay rejected.
		String url = raw.trim().replace(" ", "%20");
		if (url.isEmpty() || url.length() > MAX_LENGTH || url.chars().anyMatch(c -> c <= 0x20 || c == 0x7f)) {
			return null;
		}
		try {
			URI uri = new URI(url);
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			if (!scheme.equals("https") && !scheme.equals("http")) {
				return null;
			}
			if (uri.getHost() == null || uri.getHost().isBlank() || uri.getRawUserInfo() != null) {
				return null;
			}
			return url;
		} catch (URISyntaxException e) {
			return null;
		}
	}

	/** {@code https://doi.org/<doi>} for a normalized DOI, or null if it would not be a safe URL. */
	public static String doiUrl(String doi) {
		return doi == null ? null : safeDisplayUrl("https://doi.org/" + doi);
	}

}
