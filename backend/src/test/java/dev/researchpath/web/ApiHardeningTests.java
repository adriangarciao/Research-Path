package dev.researchpath.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import dev.researchpath.TestcontainersConfiguration;
import dev.researchpath.openalex.OpenAlexClient;
import dev.researchpath.openalex.OpenAlexException;
import dev.researchpath.openalex.ProviderTimeBudgetExceededException;
import dev.researchpath.path.PathFixtures;

/** M13 negative sweep over the public API, end to end (filters, MVC, handlers, database). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "researchpath.rate-limit.create-burst=2", "researchpath.rate-limit.creates-per-hour=1" })
@AutoConfigureMockMvc
class ApiHardeningTests {

	/** Strings that must never appear in a public error body. */
	private static final String[] INTERNALS = { "Exception", "tools.jackson", "dev.researchpath", "at org.", "SQL",
			"Bearer", "trace" };

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@MockitoBean
	OpenAlexClient openAlex;

	private static int clients;

	/** Client address for the next request; null = a fresh address per request (limits do not interfere). */
	private String ip;

	@BeforeEach
	void setUp() {
		ip = null;
		jdbc.sql("TRUNCATE candidate_snapshot, publication, concept RESTART IDENTITY CASCADE").update();
		when(openAlex.searchWorks(eq(PathFixtures.Q), eq(1), anyInt(), any())).thenReturn(PathFixtures.pool(""));
	}

	private ResultActions send(MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.with(r -> {
			r.setRemoteAddr(ip != null ? ip : "10.13." + (++clients / 250) + "." + (clients % 250 + 1));
			return r;
		}));
	}

	private static void noInternals(ResultActions result) throws Exception {
		for (String s : INTERNALS) {
			result.andExpect(content().string(not(containsString(s))));
		}
	}

	@Test
	void malformedOrWrongRequestsGetPredictableProblemsWithoutInternals() throws Exception {
		noInternals(send(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content("{\"topic\": "))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Malformed request")));
		noInternals(send(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content("[1,2,3]"))
				.andExpect(status().isBadRequest()));
		noInternals(send(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content("{\"topic\": \"a\", \"budget\": \"ten\"}"))
				.andExpect(status().isBadRequest()));
		noInternals(send(post("/api/paths").content("{\"topic\":\"graph neural networks\"}"))
				.andExpect(status().isUnsupportedMediaType()));
		noInternals(send(post("/api/paths").contentType(MediaType.TEXT_PLAIN).content("graph neural networks"))
				.andExpect(status().isUnsupportedMediaType()));
		noInternals(send(put("/api/paths").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isMethodNotAllowed()));
		noInternals(send(get("/api/does-not-exist")).andExpect(status().isNotFound()));
		noInternals(send(post("/api/paths").contentType(MediaType.APPLICATION_JSON)
				.content("{\"topic\": \"" + "x".repeat(301) + "\"}")).andExpect(status().isBadRequest()));
		noInternals(send(post("/api/paths").contentType(MediaType.APPLICATION_JSON)
				.content("{\"topic\": \"graph\", \"focus\": \"<script>\"}")).andExpect(status().isBadRequest()));
		noInternals(send(get("/api/paths/%2e%2e%2f%2e%2e")).andExpect(status().isNotFound()));
		noInternals(send(get("/api/paths/AAAAAAAAAAAAAAAAAAAAAA/revisions/99999999999")).andExpect(status().isBadRequest()));
	}

	@Test
	void oversizedBodiesAre413() throws Exception {
		String big = "{\"topic\": \"" + "x".repeat(ApiProtectionFilter.MAX_BODY_BYTES) + "\"}";
		noInternals(send(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content(big))
				.andExpect(status().isContentTooLarge()));
	}

	@Test
	void securityHeadersOnApiResponsesAndNoCrossOriginAccess() throws Exception {
		send(get("/api/paths/AAAAAAAAAAAAAAAAAAAAAA")).andExpect(status().isNotFound())
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("X-Frame-Options", "DENY"))
				.andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")));
		send(options("/api/paths").header("Origin", "https://evil.example")
				.header("Access-Control-Request-Method", "POST"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
		send(get("/api/paths/AAAAAAAAAAAAAAAAAAAAAA").header("Origin", "https://evil.example"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@Test
	void newPathsAreRateLimitedWith429AndRetryAfter() throws Exception {
		ip = "10.14.0.1";
		String body = "{\"topic\": \"reinforcement learning in robotics\", \"budget\": 5}";
		send(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
		send(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
		noInternals(send(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.title").value("Too many requests")));
		Integer paths = jdbc.sql("SELECT count(*) FROM reading_path").query(Integer.class).single();
		org.assertj.core.api.Assertions.assertThat(paths).isEqualTo(2);
	}

	@Test
	void injectionShapedTopicsAreDataNotCode() throws Exception {
		String topic = "robotics'); DROP TABLE reading_path; -- <img src=x onerror=alert(1)>";
		when(openAlex.searchWorks(eq(topic), eq(1), anyInt(), any())).thenReturn(PathFixtures.pool(""));
		send(post("/api/paths").contentType(MediaType.APPLICATION_JSON)
				.content("{\"topic\": \"" + topic.replace("\"", "\\\"") + "\", \"budget\": 5}"))
				.andExpect(status().is4xxClientError()); // no eligible papers for this topic: 422, nothing stored
		org.assertj.core.api.Assertions.assertThat(
				jdbc.sql("SELECT count(*) FROM information_schema.tables WHERE table_name = 'reading_path'")
						.query(Integer.class).single()).isEqualTo(1);
	}

	@Test
	void providerFailuresAreMappedAndStoreNothing() throws Exception {
		when(openAlex.searchWorks(anyString(), anyInt(), anyInt(), any())).thenThrow(new OpenAlexException("HTTP 500", 500, null));
		noInternals(send(post("/api/paths").contentType(MediaType.APPLICATION_JSON)
				.content("{\"topic\": \"reinforcement learning in robotics\"}"))
				.andExpect(status().isBadGateway()));
		doThrow(new OpenAlexException("HTTP 429", 429, null)).when(openAlex).searchWorks(anyString(), anyInt(), anyInt(), any());
		noInternals(send(post("/api/paths").contentType(MediaType.APPLICATION_JSON)
				.content("{\"topic\": \"reinforcement learning in robotics\"}"))
				.andExpect(status().isServiceUnavailable()));
		org.assertj.core.api.Assertions.assertThat(jdbc.sql("SELECT count(*) FROM reading_path").query(Integer.class).single())
				.isZero();
	}

	@Test
	void providerTimeBudgetIsARetryable503AndLeavesStoredPathsIntact() throws Exception {
		String created = send(post("/api/paths").contentType(MediaType.APPLICATION_JSON)
				.content("{\"topic\": \"" + PathFixtures.Q + "\", \"budget\": 5}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String pathId = com.jayway.jsonpath.JsonPath.read(created, "$.pathId");
		String before = send(get("/api/paths/" + pathId)).andReturn().getResponse().getContentAsString();

		when(openAlex.searchWorks(anyString(), anyInt(), anyInt(), any()))
				.thenThrow(new ProviderTimeBudgetExceededException("OpenAlex search page 1 stopped"));
		noInternals(send(post("/api/paths").contentType(MediaType.APPLICATION_JSON)
				.content("{\"topic\": \"graph neural networks\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(header().string("Retry-After", "10"))
				.andExpect(jsonPath("$.title").value("Research data provider too slow")));

		org.assertj.core.api.Assertions.assertThat(jdbc.sql("SELECT count(*) FROM reading_path").query(Integer.class).single())
				.isEqualTo(1);
		org.assertj.core.api.Assertions.assertThat(jdbc.sql("SELECT count(*) FROM candidate_snapshot").query(Integer.class).single())
				.isEqualTo(1);
		send(get("/api/paths/" + pathId)).andExpect(status().isOk()).andExpect(content().string(before));
	}

}
