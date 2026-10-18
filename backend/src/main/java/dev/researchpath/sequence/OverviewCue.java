package dev.researchpath.sequence;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Weak, documented cue that a work is an overview (D09 OPEN): the provider's publication type is "review", or the
 * title contains a survey-like word. It describes metadata, not verified content, and is never a difficulty
 * rating.
 */
public final class OverviewCue {

	private static final Pattern TITLE_WORD = Pattern.compile("\\b(survey|review|tutorial|introduction|overview|primer)\\b");

	private OverviewCue() {
	}

	/** Where the cue came from; explanations name it. */
	public enum Source {
		PUBLICATION_TYPE, TITLE
	}

	/** The cue's source, or null when the metadata does not mark the work as an overview. */
	public static Source of(String workType, String title) {
		if ("review".equals(workType)) {
			return Source.PUBLICATION_TYPE;
		}
		return titleWord(title) == null ? null : Source.TITLE;
	}

	/** The first survey-like word in the title (lower case), or null. */
	public static String titleWord(String title) {
		if (title == null) {
			return null;
		}
		Matcher m = TITLE_WORD.matcher(title.toLowerCase(Locale.ROOT));
		return m.find() ? m.group(1) : null;
	}

}
