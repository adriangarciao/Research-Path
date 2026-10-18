package dev.researchpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** M13 DB fault: a failing migration leaves no half-applied schema and the previous version intact. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MigrationFaultTests {

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@Test
	void failedMigrationRollsBackCompletelyInAnIsolatedSchema() {
		jdbc.sql("DROP SCHEMA IF EXISTS migration_fault CASCADE").update();
		Flyway flyway = Flyway.configure().dataSource(dataSource).schemas("migration_fault").createSchemas(true)
				.locations("classpath:db/migration", "classpath:broken-migration").load();

		assertThatThrownBy(flyway::migrate).hasMessageContaining("V7");

		assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");
		assertThat(jdbc.sql("""
				SELECT count(*) FROM information_schema.tables
				WHERE table_schema = 'migration_fault' AND table_name = 'half_done'
				""").query(Integer.class).single()).as("partial DDL rolled back").isZero();
		assertThat(jdbc.sql("""
				SELECT count(*) FROM information_schema.tables
				WHERE table_schema = 'migration_fault' AND table_name = 'path_revision'
				""").query(Integer.class).single()).as("earlier migrations intact").isEqualTo(1);
		jdbc.sql("DROP SCHEMA migration_fault CASCADE").update();
	}

}
