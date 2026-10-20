package dev.researchpath.openalex;

/**
 * The generation's overall provider time budget ran out before the next attempt (or retry wait) could finish inside
 * it. No further request was sent and nothing was persisted; retryable.
 */
public class ProviderTimeBudgetExceededException extends OpenAlexException {

	public ProviderTimeBudgetExceededException(String message) {
		super(message, null, null);
	}

}
