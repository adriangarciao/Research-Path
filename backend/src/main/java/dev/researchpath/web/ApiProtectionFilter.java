package dev.researchpath.web;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
	private final ClientIpResolver clients;

	/** Test slices (e.g. {@code @WebMvcTest}) do not bind configuration properties: then the defaults apply. */
	@Autowired
	ApiProtectionFilter(ObjectProvider<RateLimitProperties> limits, ObjectProvider<ClientIpProperties> clientIp) {
		this(limits.getIfAvailable(RateLimitProperties::defaults), clientIp.getIfAvailable(ClientIpProperties::defaults),
				System::nanoTime);
	}

	ApiProtectionFilter(RateLimitProperties limits, java.util.function.LongSupplier nanoClock) {
		this(limits, ClientIpProperties.defaults(), nanoClock);
	}

	ApiProtectionFilter(RateLimitProperties limits, ClientIpProperties clientIp, java.util.function.LongSupplier nanoClock) {
		this.limits = limits;
		this.clients = new ClientIpResolver(clientIp);
		this.creates = new TokenBucketLimiter(limits.createBurst(), limits.createsPerHour(), limits.maxClients(), nanoClock);
		this.regenerations = new TokenBucketLimiter(limits.regenerateBurst(), limits.regenerationsPerHour(),
				limits.maxClients(), nanoClock);
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
		boolean limited = path.equals("/api/paths") || REGENERATE.matcher(path).matches();
		if (limits.enabled() && "POST".equals(request.getMethod()) && limited) {
			java.util.Optional<String> resolved = clients.resolve(request);
			if (resolved.isEmpty()) {
				// A trusted edge always sets the header; a missing or ambiguous value is never guessed at.
				log.warn("Rejected a rate-limited request without a single valid client address in the trusted header");
				problem(response, HttpStatus.BAD_REQUEST, "Client address unavailable",
						"The request could not be attributed to a client address.", null);
				return;
			}
			String client = resolved.get();
			if (path.equals("/api/paths")) {
				// The global daily spend cap is the durable provider call budget (JdbcCallBudget, V8).
				long wait = creates.tryAcquire(client);
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
