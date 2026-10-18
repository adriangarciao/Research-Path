package dev.researchpath.publication;

/** Outcome of normalizing one provider record: usable work, or a recorded rejection. */
public sealed interface NormalizationResult {

	record Accepted(NormalizedWork work) implements NormalizationResult {
	}

	/** {@code providerId} is whatever identifier the record had, possibly null. */
	record Rejected(String providerId, RejectionReason reason) implements NormalizationResult {
	}

	enum RejectionReason {
		/** No valid OpenAlex work id: the record cannot be identified reliably. */
		MISSING_OR_INVALID_ID,
		/** No usable title to display or match on. */
		MISSING_TITLE
	}

}
