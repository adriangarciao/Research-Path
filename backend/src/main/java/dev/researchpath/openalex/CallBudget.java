package dev.researchpath.openalex;

/**
 * Daily budget of provider calls. {@link #tryReserve()} is called before every HTTP attempt (retries included);
 * {@link #refund()} only when an attempt certainly never reached the provider (no connection was made).
 */
public interface CallBudget {

	/** No limit (unit tests of the client and tools that do not touch the database). */
	CallBudget UNLIMITED = new CallBudget() {
		@Override
		public boolean tryReserve() {
			return true;
		}

		@Override
		public void refund() {
		}

		@Override
		public long secondsUntilReset() {
			return 0;
		}
	};

	/** Atomically takes one call from today's budget; false when it is used up. */
	boolean tryReserve();

	/** Gives back one call reserved today (never below zero). */
	void refund();

	/** Seconds until the budget resets (midnight UTC). */
	long secondsUntilReset();

}
