package dev.researchpath.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.researchpath.openalex.OpenAlexException;
import dev.researchpath.path.PathExceptions;
import dev.researchpath.snapshot.SnapshotNotEvaluatedException;

/** Maps domain failures to RFC 9457 problem responses. Bean-validation errors use Spring's defaults. */
@RestControllerAdvice
class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail badRequest(IllegalArgumentException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
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
