package dev.researchpath.path;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.researchpath.TestcontainersConfiguration;
import dev.researchpath.openalex.OpenAlexClient;
import tools.jackson.databind.json.JsonMapper;

/** M11 HTTP contract of the path API, end to end on Testcontainers with a stubbed provider. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PathApiTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	JsonMapper json;

	@MockitoBean
	OpenAlexClient openAlex;

	@BeforeEach
	void setUp() {
		jdbc.sql("TRUNCATE candidate_snapshot, publication, concept RESTART IDENTITY CASCADE").update();
		when(openAlex.searchWorks(eq(PathServiceTests.Q), eq(1), anyInt(), any())).thenReturn(PathServiceTests.pool(""));
	}

	private String createPath(String body) throws Exception {
		String response = mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return json.readTree(response).path("pathId").asString();
	}

	@Test
	void generateGetAndRegenerate() throws Exception {
		mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON)
				.content("{\"topic\": \"reinforcement learning in robotics\", \"budget\": 5}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", matchesPattern("/api/paths/[A-Za-z0-9_-]{22}/revisions/1")))
				.andExpect(header().string("Cache-Control", containsString("no-store")))
				.andExpect(jsonPath("$.revision").value(1))
				.andExpect(jsonPath("$.topic").value("reinforcement learning in robotics"))
				.andExpect(jsonPath("$.options.experience").value("INTERMEDIATE"))
				.andExpect(jsonPath("$.options.focus").value("BALANCED"))
				.andExpect(jsonPath("$.options.budget").value(5))
				.andExpect(jsonPath("$.papers.length()").value(5))
				.andExpect(jsonPath("$.papers[0].position").value(1))
				.andExpect(jsonPath("$.stages[0].label").exists())
				.andExpect(jsonPath("$.revisions.length()").value(1));
		String id = jdbc.sql("SELECT public_id FROM reading_path").query(String.class).single();
		clearInvocations(openAlex);

		mvc.perform(get("/api/paths/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.pathId").value(id))
				.andExpect(jsonPath("$.revision").value(1));

		// Missing values keep the parent's settings; only the budget changes.
		mvc.perform(post("/api/paths/" + id + "/revisions").contentType(MediaType.APPLICATION_JSON)
				.content("{\"budget\": 10}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/paths/" + id + "/revisions/2"))
				.andExpect(jsonPath("$.revision").value(2))
				.andExpect(jsonPath("$.parentRevision").value(1))
				.andExpect(jsonPath("$.options.experience").value("INTERMEDIATE"))
				.andExpect(jsonPath("$.options.budget").value(10))
				.andExpect(jsonPath("$.papers.length()").value(10))
				.andExpect(jsonPath("$.revisions.length()").value(2));
		// The same request again returns the stored revision instead of creating another.
		mvc.perform(post("/api/paths/" + id + "/revisions").contentType(MediaType.APPLICATION_JSON)
				.content("{\"budget\": 10, \"parentRevision\": 1}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2));

		mvc.perform(get("/api/paths/" + id + "/revisions/1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.revision").value(1)).andExpect(jsonPath("$.papers.length()").value(5));
		verify(openAlex, never()).searchWorks(anyString(), anyInt(), anyInt(), any());
	}

	@Test
	void invalidRequestsGetProblemResponses() throws Exception {
		mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content("{\"topic\": \"\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON)
				.content("{\"topic\": \"reinforcement learning in robotics\", \"budget\": 7}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("budget must be one of 5, 10, 15 or 20"));
		mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON)
				.content("{\"topic\": \"reinforcement learning in robotics\", \"experience\": \"expert\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("experience must be one of")));
		mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON)
				.header("Idempotency-Key", "no").content("{\"topic\": \"reinforcement learning in robotics\"}"))
				.andExpect(status().isBadRequest());
		verify(openAlex, never()).searchWorks(anyString(), anyInt(), anyInt(), any());

		mvc.perform(get("/api/paths/not-a-path")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Path not found"));
		mvc.perform(get("/api/paths/" + PathService.newPublicId())).andExpect(status().isNotFound());
		String id = createPath("{\"topic\": \"reinforcement learning in robotics\"}");
		mvc.perform(get("/api/paths/" + id + "/revisions/9")).andExpect(status().isNotFound());
		mvc.perform(get("/api/paths/" + id + "/revisions/abc")).andExpect(status().isBadRequest());
		mvc.perform(post("/api/paths/" + id + "/revisions").contentType(MediaType.APPLICATION_JSON)
				.content("{\"parentRevision\": 4}")).andExpect(status().isNotFound());
		mvc.perform(post("/api/paths/" + id + "/revisions").contentType(MediaType.APPLICATION_JSON)
				.content("{\"focus\": \"newest\"}")).andExpect(status().isBadRequest());
	}

	@Test
	void topicWithoutEligiblePapersIs422AndIdempotentRetryReturns200() throws Exception {
		when(openAlex.searchWorks(eq("sourdough baking"), eq(1), anyInt(), any()))
				.thenReturn(dev.researchpath.snapshot.WorkJsonAccess.page(1, 1,
						dev.researchpath.snapshot.WorkJsonAccess.work("W90", "Graph neural networks")));
		mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content("{\"topic\": \"sourdough baking\"}"))
				.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.title").value("No eligible papers"));

		String body = "{\"topic\": \"reinforcement learning in robotics\"}";
		mvc.perform(post("/api/paths").header("Idempotency-Key", "retry-key-123").contentType(MediaType.APPLICATION_JSON)
				.content(body)).andExpect(status().isCreated());
		mvc.perform(post("/api/paths").header("Idempotency-Key", "retry-key-123").contentType(MediaType.APPLICATION_JSON)
				.content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
		mvc.perform(post("/api/paths").header("Idempotency-Key", "retry-key-123").contentType(MediaType.APPLICATION_JSON)
				.content("{\"topic\": \"reinforcement learning in robotics\", \"budget\": 20}"))
				.andExpect(status().isConflict());
	}

}
