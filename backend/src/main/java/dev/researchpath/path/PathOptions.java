package dev.researchpath.path;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import dev.researchpath.optimizer.Preferences;
import dev.researchpath.optimizer.Preferences.Experience;
import dev.researchpath.optimizer.Preferences.Focus;

/** User-editable settings of one revision (blueprint §2.1). Defaults: Intermediate, Balanced, 10 papers. */
public record PathOptions(Experience experience, Focus focus, int budget) {

	public static final List<Integer> BUDGETS = List.of(5, 10, 15, 20);

	public static final PathOptions DEFAULT = new PathOptions(Experience.INTERMEDIATE, Focus.BALANCED, 10);

	public PathOptions {
		if (experience == null || focus == null) {
			throw new IllegalArgumentException("experience and focus are required");
		}
		if (!BUDGETS.contains(budget)) {
			throw new IllegalArgumentException("budget must be one of 5, 10, 15 or 20");
		}
	}

	public Preferences preferences() {
		return new Preferences(experience, focus);
	}

	/** Parses request values; a missing value keeps the one from {@code fallback}. */
	public static PathOptions parse(String experience, String focus, Integer budget, PathOptions fallback) {
		return new PathOptions(
				experience == null ? fallback.experience() : parse(Experience.class, "experience", experience),
				focus == null ? fallback.focus() : parse(Focus.class, "focus", focus),
				budget == null ? fallback.budget() : budget);
	}

	private static <E extends Enum<E>> E parse(Class<E> type, String field, String value) {
		try {
			return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(field + " must be one of " + Arrays.stream(type.getEnumConstants())
					.map(c -> c.name().toLowerCase(Locale.ROOT)).toList());
		}
	}

}
