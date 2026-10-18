package dev.researchpath.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.researchpath.openalex.OpenAlexException;

/** Maps domain failures to RFC 9457 problem responses. Bean-validation errors use Spring's defaults. */
@RestControllerAdvice
class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail badRequest(IllegalArgumentException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	/** Provider unavailable or rate-limited: retryable for the caller, nothing was persisted. */
	@ExceptionHandler(OpenAlexException.class)
	ProblemDetail providerFailure(OpenAlexException e) {
		log.warn("OpenAlex failure: {}", e.getMessage());
		HttpStatus status = e.isRateLimited() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
				e.isRateLimited() ? "Research data provider rate limit or daily budget reached; try again later."
						: "Research data provider request failed; try again later.");
		problem.setTitle("Research data provider unavailable");
		if (e.httpStatus() != null) {
			problem.setProperty("providerStatus", e.httpStatus());
		}
		return problem;
	}

}
