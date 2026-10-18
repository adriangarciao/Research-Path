package dev.researchpath.relevance;

/**
 * Relevance gate outcome for one candidate (decision D07, conservative). Only {@link #ELIGIBLE}
 * candidates may enter optimization; every other tier is kept with its evidence but excluded.
 */
public enum RelevanceTier {
	/** Strong evidence: most query terms in the title or confident keywords/topics. */
	ELIGIBLE,
	/** Title is mainly about an aspect the query did not ask for (policy, legal, education, ...). */
	TANGENTIAL,
	/** Only partial term evidence. Excluded by default (conservative), kept for review. */
	BORDERLINE,
	/** No meaningful term evidence for the query. */
	INELIGIBLE;

	public boolean eligible() {
		return this == ELIGIBLE;
	}
}
