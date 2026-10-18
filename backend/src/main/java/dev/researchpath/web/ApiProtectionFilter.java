package dev.researchpath.web;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * First line of defence for every request (playbook M13): security headers on all responses, a body size cap on
 * API requests, and rate limits on the two expensive operations (new path, regeneration). Refusals are RFC 9457
 * problem responses without internal details.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApiProtectionFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(ApiProtectionFilter.class);

	/** Path API bodies are a topic and three options; 8 KB is generous. */
	static final int MAX_BODY_BYTES = 8 * 1024;

	private static final Pattern REGENERATE = Pattern.compile("^/api/paths/[^/]+/revisions$");

	private final RateLimitProperties limits;
	private final TokenBucketLimiter creates;
	private final TokenBucketLimiter regenerations;
	private final Clock clock;
	private LocalDate day;
	private int createsToday;

	/** Test slices (e.g. {@code @WebMvcTest}) do not bind configuration properties: then the defaults apply. */
	@Autowired
	ApiProtectionFilter(ObjectProvider<RateLimitProperties> limits) {
		this(limits.getIfAvailable(RateLimitProperties::defaults), System::nanoTime, Clock.systemUTC());
	}

	ApiProtectionFilter(RateLimitProperties limits, java.util.function.LongSupplier nanoClock, Clock clock) {
		this.limits = limits;
		this.creates = new TokenBucketLimiter(limits.createBurst(), limits.createsPerHour(), limits.maxClients(), nanoClock);
		this.regenerations = new TokenBucketLimiter(limits.regenerateBurst(), limits.regenerationsPerHour(),
				limits.maxClients(), nanoClock);
		this.clock = clock;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		securityHeaders(response);
		String path = request.getRequestURI().substring(request.getContextPath().length());
		if (!path.startsWith("/api/")) {
			chain.doFilter(request, response);
			return;
		}
		if (request.getContentLengthLong() > MAX_BODY_BYTES) {
			problem(response, HttpStatus.CONTENT_TOO_LARGE, "Request too large",
					"Request bodies are limited to " + MAX_BODY_BYTES + " bytes.", null);
			return;
		}
		if (limits.enabled() && "POST".equals(request.getMethod())) {
			String client = request.getRemoteAddr();
			if (path.equals("/api/paths")) {
				long wait = creates.tryAcquire(client);
				if (wait == 0 && !takeDailyCreate()) {
					log.warn("Daily new-path cap ({}) reached", limits.createsPerDay());
					problem(response, HttpStatus.TOO_MANY_REQUESTS, "Daily limit reached",
							"The service has reached its daily limit for new paths. Existing paths still work; try again tomorrow.",
							secondsUntilTomorrow());
					return;
				}
				if (wait > 0) {
					problem(response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests",
							"Too many new paths from this connection. Please wait before starting another.", wait);
					return;
				}
			} else if (REGENERATE.matcher(path).matches()) {
				long wait = regenerations.tryAcquire(client);
				if (wait > 0) {
					problem(response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests",
							"Too many rebuilds from this connection. Please wait a moment.", wait);
					return;
				}
			}
		}
		chain.doFilter(new BoundedBodyRequest(request), response);
	}

	private synchronized boolean takeDailyCreate() {
		LocalDate today = LocalDate.now(clock);
		if (!today.equals(day)) {
			day = today;
			createsToday = 0;
		}
		if (createsToday >= limits.createsPerDay()) {
			return false;
		}
		createsToday++;
		return true;
	}

	private long secondsUntilTomorrow() {
		long now = clock.instant().getEpochSecond();
		long midnight = LocalDate.now(clock).plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
		return Math.max(1, midnight - now);
	}

	private static void securityHeaders(HttpServletResponse response) {
		response.setHeader("X-Content-Type-Options", "nosniff");
		response.setHeader("X-Frame-Options", "DENY");
		response.setHeader("Referrer-Policy", "no-referrer");
		response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
		response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
	}

	private static void problem(HttpServletResponse response, HttpStatus status, String title, String detail,
			Long retryAfter) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		if (retryAfter != null) {
			response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
		}
		String body = "{\"type\":\"about:blank\",\"title\":" + quote(title) + ",\"status\":" + status.value()
				+ ",\"detail\":" + quote(detail) + "}";
		response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
	}

	private static String quote(String s) {
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	/** Enforces the size cap also for bodies without a Content-Length (chunked). */
	static final class BoundedBodyRequest extends HttpServletRequestWrapper {

		BoundedBodyRequest(HttpServletRequest request) {
			super(request);
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			InputStream in = super.getInputStream();
			byte[] body = in.readNBytes(MAX_BODY_BYTES + 1);
			if (body.length > MAX_BODY_BYTES) {
				throw new BodyTooLargeException();
			}
			ByteArrayInputStream bytes = new ByteArrayInputStream(body);
			return new ServletInputStream() {
				@Override
				public int read() {
					return bytes.read();
				}

				@Override
				public int read(byte[] b, int off, int len) {
					return bytes.read(b, off, len);
				}

				@Override
				public boolean isFinished() {
					return bytes.available() == 0;
				}

				@Override
				public boolean isReady() {
					return true;
				}

				@Override
				public void setReadListener(ReadListener listener) {
					throw new UnsupportedOperationException();
				}
			};
		}
	}

	/** A body over the cap was streamed without a Content-Length. */
	static final class BodyTooLargeException extends IOException {
		BodyTooLargeException() {
			super("request body exceeds " + MAX_BODY_BYTES + " bytes");
		}
	}

}
