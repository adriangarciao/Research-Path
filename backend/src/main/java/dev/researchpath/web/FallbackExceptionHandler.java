package dev.researchpath.web;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Last resort: standard MVC errors (404 route, 405, 415, validation …) keep their own status and problem body;
 * anything else becomes a generic 500 with an incident id. Exception messages, class names and stack traces are
 * logged, never returned.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class FallbackExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(FallbackExceptionHandler.class);

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> unexpected(Exception e) {
		if (e instanceof ErrorResponse standard) {
			return ResponseEntity.status(standard.getStatusCode()).headers(standard.getHeaders()).body(standard.getBody());
		}
		String incident = UUID.randomUUID().toString();
		log.error("Unexpected error (incident {})", incident, e);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"Something went wrong on our side. Please try again.");
		problem.setTitle("Unexpected error");
		problem.setProperty("incident", incident);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
	}

}
