package dev.researchpath.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Refuses to start when the test-only {@code e2e} profile (relaxed limits) is combined with {@code prod}. */
@Configuration(proxyBeanMethods = false)
@Profile("prod & e2e")
class TestProfileGuard {

	TestProfileGuard() {
		throw new IllegalStateException("The 'e2e' profile relaxes rate limits for local tests and must never run with 'prod'.");
	}

}
