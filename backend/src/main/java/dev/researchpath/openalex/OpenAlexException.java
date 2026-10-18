package dev.researchpath.openalex;

/**
 * Provider failure after retries. {@code httpStatus} is null for network errors.
 * Messages never contain credentials (the key is only ever sent as a header).
 */
public class OpenAlexException extends RuntimeException {

	private final Integer httpStatus;

	public OpenAlexException(String message, Integer httpStatus, Throwable cause) {
		super(message, cause);
		this.httpStatus = httpStatus;
	}

	public Integer httpStatus() {
		return httpStatus;
	}

	public boolean isRateLimited() {
		return httpStatus != null && httpStatus == 429;
	}

}
