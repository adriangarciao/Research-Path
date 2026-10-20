package dev.researchpath.openalex;

/** Today's provider call budget is used up; no request was sent. Retryable after {@link #retryAfterSeconds()}. */
public class ProviderBudgetExhaustedException extends RuntimeException {

	private final long retryAfterSeconds;

	public ProviderBudgetExhaustedException(long retryAfterSeconds) {
		super("Daily provider call budget exhausted");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long retryAfterSeconds() {
		return retryAfterSeconds;
	}

}
