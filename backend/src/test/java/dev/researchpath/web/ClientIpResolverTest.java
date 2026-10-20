package dev.researchpath.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Client identity for rate limiting: off by default, strict single-IP parsing when a trusted header is set. */
class ClientIpResolverTest {

	private static final ClientIpResolver OFF = new ClientIpResolver(ClientIpProperties.defaults());
	private static final ClientIpResolver REAL_IP = new ClientIpResolver(new ClientIpProperties("X-Real-IP"));

	private static MockHttpServletRequest request(String remote, String... realIp) {
		MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/paths");
		r.setRemoteAddr(remote);
		for (String v : realIp) {
			r.addHeader("X-Real-IP", v);
		}
		return r;
	}

	@Test
	void offByDefaultIgnoresEveryClientSuppliedHeader() {
		MockHttpServletRequest r = request("10.0.0.7", "203.0.113.9");
		r.addHeader("X-Forwarded-For", "198.51.100.1");
		r.addHeader("Forwarded", "for=198.51.100.2");
		assertThat(OFF.resolve(r)).contains("10.0.0.7");
	}

	@Test
	void trustedHeaderWithOneValidAddressIsUsed() {
		assertThat(REAL_IP.resolve(request("10.0.0.7", "203.0.113.9"))).contains("203.0.113.9");
		assertThat(REAL_IP.resolve(request("10.0.0.7", " 203.0.113.9 "))).contains("203.0.113.9");
		assertThat(REAL_IP.resolve(request("10.0.0.7", "2001:db8::1"))).contains("2001:db8:0:0:0:0:0:1");
		assertThat(REAL_IP.resolve(request("10.0.0.7", "2001:DB8:0:0:0:0:0:1"))).contains("2001:db8:0:0:0:0:0:1");
	}

	@Test
	void forwardedForAndForwardedAreNeverUsedEvenWhenTheTrustedHeaderIsMissing() {
		MockHttpServletRequest r = request("10.0.0.7");
		r.addHeader("X-Forwarded-For", "198.51.100.1");
		r.addHeader("Forwarded", "for=198.51.100.2");
		assertThat(REAL_IP.resolve(r)).isEmpty();
	}

	@Test
	void malformedOrAmbiguousValuesAreRejected() {
		for (String bad : new String[] { "", " ", "203.0.113.9, 198.51.100.1", "203.0.113.9 198.51.100.1",
				"203.0.113.9:443", "[2001:db8::1]", "[2001:db8::1]:443", "fe80::1%eth0", "256.1.1.1", "1.2.3",
				"01.2.3.4", "localhost", "evil.example", "unknown", "for=203.0.113.9", "1.2.3.4\r\nX-Evil: 1",
				"::::::::", "1".repeat(50), "203.0.113.9;" }) {
			assertThat(REAL_IP.resolve(request("10.0.0.7", bad))).as("'%s'", bad).isEmpty();
		}
		assertThat(REAL_IP.resolve(request("10.0.0.7", "203.0.113.9", "198.51.100.1"))).as("repeated header").isEmpty();
		assertThat(REAL_IP.resolve(request("10.0.0.7"))).as("missing").isEmpty();
	}

	@Test
	void filterRateLimitsPerTrustedAddressAndRefusesUnusableOnes() throws Exception {
		RateLimitProperties limits = new RateLimitProperties(true, 1, 1, 1, 1, 1000, 100);
		ApiProtectionFilter filter = new ApiProtectionFilter(limits, new ClientIpProperties("X-Real-IP"),
				new AtomicLong()::get, Clock.systemUTC());

		// Same edge connection, two different clients: independent buckets.
		assertThat(run(filter, request("10.0.0.7", "203.0.113.9")).getStatus()).isEqualTo(200);
		assertThat(run(filter, request("10.0.0.7", "203.0.113.10")).getStatus()).isEqualTo(200);
		assertThat(run(filter, request("10.0.0.7", "203.0.113.9")).getStatus()).isEqualTo(429);
		// Changing X-Forwarded-For does not create a new identity.
		MockHttpServletRequest xff = request("10.0.0.7", "203.0.113.9");
		xff.addHeader("X-Forwarded-For", "198.51.100.77");
		assertThat(run(filter, xff).getStatus()).isEqualTo(429);

		MockHttpServletResponse refused = run(filter, request("10.0.0.7", "203.0.113.9, 198.51.100.1"));
		assertThat(refused.getStatus()).isEqualTo(400);
		assertThat(refused.getContentAsString()).contains("Client address unavailable").doesNotContain("198.51.100.1");
		assertThat(run(filter, request("10.0.0.7")).getStatus()).isEqualTo(400);

		// Reads are never refused for identity reasons.
		MockHttpServletRequest read = new MockHttpServletRequest("GET", "/api/paths/AAAAAAAAAAAAAAAAAAAAAA");
		read.setRemoteAddr("10.0.0.7");
		assertThat(run(filter, read).getStatus()).isEqualTo(200);
	}

	private static MockHttpServletResponse run(ApiProtectionFilter f, MockHttpServletRequest r) throws Exception {
		r.setContentType("application/json");
		r.setContent("{\"topic\":\"x\"}".getBytes());
		MockHttpServletResponse response = new MockHttpServletResponse();
		f.doFilter(r, response, new MockFilterChain());
		return response;
	}

}
