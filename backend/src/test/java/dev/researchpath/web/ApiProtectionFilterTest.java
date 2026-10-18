package dev.researchpath.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletRequest;

class ApiProtectionFilterTest {

	private static final RateLimitProperties LIMITS = new RateLimitProperties(true, 2, 10, 3, 60, 1000, 100);

	private final AtomicLong nanos = new AtomicLong();

	private ApiProtectionFilter filter(RateLimitProperties limits, Clock clock) {
		return new ApiProtectionFilter(limits, nanos::get, clock);
	}

	private static MockHttpServletRequest post(String uri, String ip) {
		MockHttpServletRequest r = new MockHttpServletRequest("POST", uri);
		r.setRemoteAddr(ip);
		r.setContentType("application/json");
		r.setContent("{\"topic\":\"x\"}".getBytes());
		return r;
	}

	private static MockHttpServletResponse run(ApiProtectionFilter f, MockHttpServletRequest request) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		f.doFilter(request, response, new MockFilterChain());
		return response;
	}

	@Test
	void everyResponseCarriesSecurityHeaders() throws Exception {
		MockHttpServletResponse r = run(filter(LIMITS, Clock.systemUTC()), new MockHttpServletRequest("GET", "/actuator/health"));
		assertThat(r.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
		assertThat(r.getHeader("X-Frame-Options")).isEqualTo("DENY");
		assertThat(r.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
		assertThat(r.getHeader("Content-Security-Policy")).contains("default-src 'none'").contains("frame-ancestors 'none'");
	}

	@Test
	void newPathsAndRebuildsHaveSeparateLimitsPerClientAndReadsAreNotLimited() throws Exception {
		ApiProtectionFilter f = filter(LIMITS, Clock.systemUTC());
		assertThat(run(f, post("/api/paths", "10.0.0.1")).getStatus()).isEqualTo(200);
		assertThat(run(f, post("/api/paths", "10.0.0.1")).getStatus()).isEqualTo(200);
		MockHttpServletResponse limited = run(f, post("/api/paths", "10.0.0.1"));
		assertThat(limited.getStatus()).isEqualTo(429);
		assertThat(limited.getHeader("Retry-After")).isEqualTo("360");
		assertThat(limited.getContentType()).isEqualTo("application/problem+json");
		assertThat(limited.getContentAsString()).contains("\"status\":429").contains("Too many new paths");

		assertThat(run(f, post("/api/paths", "10.0.0.2")).getStatus()).as("other client").isEqualTo(200);
		for (int i = 0; i < 3; i++) {
			assertThat(run(f, post("/api/paths/AAAAAAAAAAAAAAAAAAAAAA/revisions", "10.0.0.1")).getStatus()).isEqualTo(200);
		}
		assertThat(run(f, post("/api/paths/AAAAAAAAAAAAAAAAAAAAAA/revisions", "10.0.0.1")).getStatus()).isEqualTo(429);
		for (int i = 0; i < 20; i++) {
			MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/paths/AAAAAAAAAAAAAAAAAAAAAA");
			get.setRemoteAddr("10.0.0.1");
			assertThat(run(f, get).getStatus()).isEqualTo(200);
		}
		nanos.addAndGet(360_000_000_000L);
		assertThat(run(f, post("/api/paths", "10.0.0.1")).getStatus()).as("refilled").isEqualTo(200);
	}

	@Test
	void globalDailyCapStopsNewPathsUntilTheNextUtcDay() throws Exception {
		AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-18T23:00:00Z"));
		Clock clock = new Clock() {
			@Override
			public ZoneOffset getZone() {
				return ZoneOffset.UTC;
			}

			@Override
			public Clock withZone(java.time.ZoneId zone) {
				return this;
			}

			@Override
			public Instant instant() {
				return now.get();
			}
		};
		ApiProtectionFilter f = filter(new RateLimitProperties(true, 100, 100, 100, 100, 2, 100), clock);
		assertThat(run(f, post("/api/paths", "10.0.0.1")).getStatus()).isEqualTo(200);
		assertThat(run(f, post("/api/paths", "10.0.0.2")).getStatus()).isEqualTo(200);
		MockHttpServletResponse capped = run(f, post("/api/paths", "10.0.0.3"));
		assertThat(capped.getStatus()).isEqualTo(429);
		assertThat(capped.getHeader("Retry-After")).isEqualTo("3600");
		assertThat(capped.getContentAsString()).contains("daily limit");
		now.set(Instant.parse("2026-10-19T00:00:01Z"));
		assertThat(run(f, post("/api/paths", "10.0.0.3")).getStatus()).isEqualTo(200);
	}

	@Test
	void oversizedBodiesAreRefusedWithOrWithoutContentLength() throws Exception {
		ApiProtectionFilter f = filter(LIMITS, Clock.systemUTC());
		MockHttpServletRequest big = post("/api/paths", "10.0.0.9");
		big.setContent(new byte[ApiProtectionFilter.MAX_BODY_BYTES + 1]);
		MockHttpServletResponse r = run(f, big);
		assertThat(r.getStatus()).isEqualTo(413);

		// Chunked: no Content-Length, so the cap is enforced while reading.
		MockHttpServletRequest chunked = new MockHttpServletRequest("POST", "/api/paths/AAAAAAAAAAAAAAAAAAAAAA/revisions") {
			@Override
			public long getContentLengthLong() {
				return -1;
			}
		};
		chunked.setContent(new byte[ApiProtectionFilter.MAX_BODY_BYTES + 1]);
		AtomicReference<HttpServletRequest> seen = new AtomicReference<>();
		f.doFilter(chunked, new MockHttpServletResponse(), (req, res) -> seen.set((HttpServletRequest) req));
		assertThatThrownBy(() -> seen.get().getInputStream()).isInstanceOf(ApiProtectionFilter.BodyTooLargeException.class);

		MockHttpServletRequest small = post("/api/paths/AAAAAAAAAAAAAAAAAAAAAA/revisions", "10.0.0.9");
		f.doFilter(small, new MockHttpServletResponse(), (req, res) -> seen.set((HttpServletRequest) req));
		assertThat(new String(seen.get().getInputStream().readAllBytes())).isEqualTo("{\"topic\":\"x\"}");
	}

	@Test
	void limitsCanBeDisabled() throws Exception {
		ApiProtectionFilter f = filter(new RateLimitProperties(false, 1, 1, 1, 1, 1, 1), Clock.systemUTC());
		for (int i = 0; i < 5; i++) {
			assertThat(run(f, post("/api/paths", "10.0.0.1")).getStatus()).isEqualTo(200);
		}
	}

}
