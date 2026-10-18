package dev.researchpath.optimizer;

/** User controls from the blueprint (§2.1). Defaults: Intermediate, Balanced. */
public record Preferences(Experience experience, Focus focus) {

	public static final Preferences DEFAULT = new Preferences(Experience.INTERMEDIATE, Focus.BALANCED);

	public enum Experience {
		BEGINNER, INTERMEDIATE, ADVANCED
	}

	public enum Focus {
		BALANCED, FOUNDATIONAL, RECENT
	}

}
