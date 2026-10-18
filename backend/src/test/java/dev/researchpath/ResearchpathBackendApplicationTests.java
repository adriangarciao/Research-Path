package dev.researchpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Environment smoke tests: context, Flyway, real DB round-trip and health. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "management.endpoint.health.show-details=always")
@AutoConfigureMockMvc
class ResearchpathBackendApplicationTests {

	@Autowired
	Flyway flyway;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MockMvc mockMvc;

	@Test
	void flywayAppliedAllMigrations() {
		MigrationInfo current = flyway.info().current();
		assertThat(current).isNotNull();
		assertThat(current.getVersion().getVersion()).isEqualTo("5");
		assertThat(flyway.info().pending()).isEmpty();
		assertThat(flyway.info().applied()).allSatisfy(m -> assertThat(m.getState().isFailed()).isFalse());
	}

	@Test
	void publicationAndSnapshotTablesExist() {
		Integer tables = jdbc.queryForObject("""
				SELECT count(*) FROM information_schema.tables
				WHERE table_schema = 'public' AND table_name IN (
				  'publication', 'publication_author', 'concept', 'publication_concept',
				  'publication_reference', 'publication_identity_candidate',
				  'candidate_snapshot', 'snapshot_membership')
				""", Integer.class);
		assertThat(tables).isEqualTo(8);
	}

	@Test
	void databaseIsPostgres17() {
		String version = jdbc.queryForObject("SHOW server_version", String.class);
		assertThat(version).startsWith("17.");
	}

	@Test
	void devApiIsDisabledByDefault() throws Exception {
		mockMvc.perform(post("/api/dev/snapshots").contentType(MediaType.APPLICATION_JSON).content("{\"topic\":\"t\"}"))
			.andExpect(status().isNotFound());
	}

	@Test
	void healthReportsDatabaseUp() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.components.db.status").value("UP"))
			.andExpect(jsonPath("$.components.db.details.database").value("PostgreSQL"));
	}

}
