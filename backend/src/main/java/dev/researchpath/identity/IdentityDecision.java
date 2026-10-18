package dev.researchpath.identity;

/**
 * Outcome of the identity policy for one candidate pair. Only {@link #SAME_WORK} has an effect
 * (the non-canonical member is suppressed from a snapshot's candidate set); every other decision
 * keeps both works eligible. Records are never merged or deleted.
 */
public enum IdentityDecision {
	/** Obvious duplicate: a reader gains nothing from both. Suppress the non-canonical one. */
	SAME_WORK,
	/** Likely another version of the same work (e.g. preprint vs. journal). Kept separate, flagged. */
	PROBABLE_VERSION,
	/** Evidence indicates different works sharing a title. Kept separate. */
	DISTINCT,
	/** Not enough evidence either way (missing authors/year, DOI/title conflict). Kept separate. */
	UNRESOLVED;

	public boolean suppresses() {
		return this == SAME_WORK;
	}
}
