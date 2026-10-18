package dev.researchpath.path;

/** Path failures mapped to HTTP problems by {@code ApiExceptionHandler}. */
public final class PathExceptions {

	private PathExceptions() {
	}

	/** Unknown or malformed path id or revision (404; the same answer for both, so ids cannot be probed). */
	public static class NotFound extends RuntimeException {
		public NotFound() {
			super("No path with this id and revision.");
		}
	}

	/** Too many generations running (503, retryable). */
	public static class Busy extends RuntimeException {
		public Busy() {
			super("Too many paths are being generated right now; try again shortly.");
		}
	}

	/** Revision limit reached, or an idempotency key reused for a different request (409). */
	public static class Conflict extends RuntimeException {
		public Conflict(String message) {
			super(message);
		}
	}

	/** The topic produced no eligible papers; nothing was stored as a path (422). */
	public static class NoEligiblePapers extends RuntimeException {
		public NoEligiblePapers(String topic) {
			super("No eligible papers were found for \"" + topic + "\". Try a broader or differently worded topic.");
		}
	}

}
