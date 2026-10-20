package dev.researchpath.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** The e2e relaxation can never be active together with the production profile. */
class TestProfileGuardTest {

	@Test
	void prodTogetherWithE2eRefusesToStart() {
		assertThatThrownBy(() -> {
			try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
				ctx.getEnvironment().setActiveProfiles("prod", "e2e");
				ctx.register(TestProfileGuard.class);
				ctx.refresh();
			}
		}).hasStackTraceContaining("must never run with 'prod'");
	}

	@Test
	void eachProfileAloneIsFine() {
		for (String profile : new String[] { "prod", "e2e", "local" }) {
			new ApplicationContextRunner().withUserConfiguration(TestProfileGuard.class)
					.withPropertyValues("spring.profiles.active=" + profile)
					.run(ctx -> assertThat(ctx).hasNotFailed());
		}
	}

	@Test
	void e2eProfileOnlyRelaxesTheCreateBurst() throws Exception {
		String yml = new org.springframework.core.io.ClassPathResource("application-e2e.yml")
				.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
		assertThat(yml).contains("create-burst: 20").doesNotContain("creates-per-day").doesNotContain("enabled: false");
	}

}
