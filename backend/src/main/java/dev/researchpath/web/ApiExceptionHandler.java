package dev.researchpath.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.researchpath.openalex.OpenAlexException;
import dev.researchpath.openalex.ProviderBudgetExhaustedException;
import dev.researchpath.openalex.ProviderTimeBudgetExceededException;
import dev.researchpath.path.PathExceptions;
import dev.researchpath.snapshot.SnapshotNotEvaluatedException;

/**
 * Maps domain failures to RFC 9457 problem responses. Bean-validation and other MVC errors use Spring's defaults;
 * anything unexpected falls through to {@link FallbackExceptionHandler}.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail badRequest(IllegalArgumentException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	/** Malformed JSON, or a streamed body over the size cap. The parser's own message is not echoed. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail unreadable(HttpMessageNotReadableException e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof ApiProtectionFilter.BodyTooLargeException) {
				ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE,
						"Request bodies are limited to " + ApiProtectionFilter.MAX_BODY_BYTES + " bytes.");
				problem.setTitle("Request too large");
				return problem;
			}
		}
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request body is not valid JSON for this operation.");
		problem.setTitle("Malformed request");
		return problem;
	}

	/** Database unreachable or failing: retryable, and no SQL or driver detail reaches the client. */
	@ExceptionHandler(DataAccessException.class)
	ProblemDetail database(DataAccessException e) {
		log.error("Database failure: {}", e.getClass().getSimpleName(), e);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"The service is temporarily unavailable. Stored paths are safe; please try again shortly.");
		problem.setTitle("Temporarily unavailable");
		return problem;
	}

	@ExceptionHandler(SnapshotNotEvaluatedException.class)
	ProblemDetail notEvaluated(SnapshotNotEvaluatedException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
		problem.setTitle("Snapshot predates frozen evaluation inputs");
		return problem;
	}

	@ExceptionHandler(PathExceptions.NotFound.class)
	ProblemDetail pathNotFound(PathExceptions.NotFound e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
		problem.setTitle("Path not found");
		return problem;
	}

	@ExceptionHandler(PathExceptions.Conflict.class)
	ProblemDetail pathConflict(PathExceptions.Conflict e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
		problem.setTitle("Request conflicts with a stored path");
		return problem;
	}

	@ExceptionHandler(PathExceptions.NoEligiblePapers.class)
	ProblemDetail noEligiblePapers(PathExceptions.NoEligiblePapers e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
		problem.setTitle("No eligible papers");
		return problem;
	}

	/** Generation capacity is bounded; retryable, nothing was stored. */
	@ExceptionHandler(PathExceptions.Busy.class)
	ResponseEntity<ProblemDetail> busy(PathExceptions.Busy e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		problem.setTitle("Busy");
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "5").body(problem);
	}

	/** Today's provider budget is used up: no provider call was made and nothing was stored. */
	@ExceptionHandler(ProviderBudgetExhaustedException.class)
	ResponseEntity<ProblemDetail> budgetExhausted(ProviderBudgetExhaustedException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"The service has reached its daily limit for new searches. Existing paths and rebuilds still work; "
						+ "new searches resume after 00:00 UTC.");
		problem.setTitle("Daily limit reached");
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds())).body(problem);
	}

	/** The generation's provider time budget ran out (R5): retryable, nothing was persisted. */
	@ExceptionHandler(ProviderTimeBudgetExceededException.class)
	ResponseEntity<ProblemDetail> providerTooSlow(ProviderTimeBudgetExceededException e) {
		log.warn("OpenAlex failure: {}", e.getMessage());
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"The research data provider did not respond in time, so nothing was saved. Please try again in a moment.");
		problem.setTitle("Research data provider too slow");
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "10").body(problem);
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
