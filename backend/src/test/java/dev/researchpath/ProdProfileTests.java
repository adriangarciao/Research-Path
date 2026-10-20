package dev.researchpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The production profile starts against a real database (Testcontainers supplies the connection; the Railway
 * variables only need to resolve), keeps the dev API off and the health endpoint terse, and never ships credential
 * defaults.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("prod")
@SpringBootTest(properties = { "PGHOST=unused", "PGPORT=5432", "PGDATABASE=unused", "PGUSER=unused",
		"PGPASSWORD=unused", "PORT=9123" })
@AutoConfigureMockMvc
class ProdProfileTests {

	@Autowired
	MockMvc mvc;

	@Value("${server.port}")
	int port;

	@Value("${spring.datasource.hikari.maximum-pool-size}")
	int pool;

	@Value("${server.shutdown}")
	String shutdown;

	@Test
	void startsWithPlatformPortSmallPoolAndGracefulShutdown() {
		assertThat(port).isEqualTo(9123);
		assertThat(pool).isEqualTo(5);
		assertThat(shutdown).isEqualTo("graceful");
	}

	@Test
	void healthIsUpWithoutDetailsAndDevApiIsAbsent() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components").doesNotExist())
				.andExpect(content().string(Matchers.not(Matchers.containsString("PostgreSQL"))));
		mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
		mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
		mvc.perform(get("/actuator/flyway")).andExpect(status().isNotFound());
		mvc.perform(post("/api/dev/snapshots").contentType(MediaType.APPLICATION_JSON).content("{\"topic\":\"x\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void credentialsHaveNoDefaults() throws Exception {
		String yml = new ClassPathResource("application-prod.yml").getContentAsString(StandardCharsets.UTF_8);
		for (String required : new String[] { "${PGHOST}", "${PGPORT}", "${PGDATABASE}", "${PGUSER}", "${PGPASSWORD}" }) {
			assertThat(yml).as("no fallback for %s", required).contains(required);
		}
		assertThat(yml).doesNotContainPattern("PGPASSWORD:[^}]").doesNotContainPattern("PGUSER:[^}]");
	}

}
