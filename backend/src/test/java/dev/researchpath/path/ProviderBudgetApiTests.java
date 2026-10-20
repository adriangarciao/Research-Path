package dev.researchpath.path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.researchpath.TestcontainersConfiguration;
import dev.researchpath.openalex.OpenAlexClient;

/**
 * H2 end to end: once today's provider budget is used up, new paths get 503 with Retry-After, nothing is stored and
 * no provider request is made; stored paths and rebuilds keep working. The second search goes through the real client
 * (whose provider address points nowhere) so the budget check, not a mock, refuses it.
 */
@Import(TestcontainersConfiguration.class)
// The provider base URL points nowhere: a broken budget check could never reach the real OpenAlex from this test.
@SpringBootTest(properties = { "researchpath.provider-budget.daily-calls=1", "researchpath.openalex.base-url=http://127.0.0.1:9",
		"researchpath.openalex.max-attempts=1" })
@AutoConfigureMockMvc
class ProviderBudgetApiTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@MockitoSpyBean
	OpenAlexClient openAlex;

	@BeforeEach
	void setUp() {
		jdbc.sql("TRUNCATE candidate_snapshot, publication, concept RESTART IDENTITY CASCADE").update();
		jdbc.sql("DELETE FROM provider_call_budget").update();
	}

	@Test
	void exhaustedBudgetRefusesNewPathsButNotRebuilds() throws Exception {
		// The first path is built from a stubbed search (no HTTP, so no budget used).
		org.mockito.Mockito.doReturn(PathServiceTests.pool("")).when(openAlex).searchWorks(eq(PathServiceTests.Q), eq(1), anyInt(), any());
		String body = mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON)
				.content("{\"topic\": \"reinforcement learning in robotics\", \"budget\": 5}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String id = body.replaceAll(".*\"pathId\":\"([A-Za-z0-9_-]{22})\".*", "$1");
		// Today's single call is now used up.
		jdbc.sql("INSERT INTO provider_call_budget (day, calls, updated_at) VALUES ((now() AT TIME ZONE 'UTC')::date, 1, now())")
				.update();

		org.mockito.Mockito.doCallRealMethod().when(openAlex).searchWorks(org.mockito.ArgumentMatchers.anyString(), anyInt(), anyInt(), any());
		int paths = jdbc.sql("SELECT count(*) FROM reading_path").query(Integer.class).single();
		mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content("{\"topic\": \"graph neural networks\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.title").value("Daily limit reached"));
		Assertions.assertThat(jdbc.sql("SELECT count(*) FROM reading_path").query(Integer.class).single()).isEqualTo(paths);
		Assertions.assertThat(jdbc.sql("SELECT count(*) FROM candidate_snapshot").query(Integer.class).single()).isEqualTo(1);
		Assertions.assertThat(jdbc.sql("SELECT calls FROM provider_call_budget").query(Integer.class).single())
				.as("a refused attempt does not count").isEqualTo(1);

		mvc.perform(post("/api/paths/" + id + "/revisions").contentType(MediaType.APPLICATION_JSON).content("{\"budget\": 10}"))
				.andExpect(status().isCreated());
	}

}
