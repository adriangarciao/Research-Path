package dev.researchpath.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.researchpath.TestcontainersConfiguration;
import dev.researchpath.openalex.OpenAlexClient;
import dev.researchpath.path.PathRepository;

/** M13: database outages and unexpected failures degrade to safe, generic problems. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class FaultMappingTests {

	private static final String ID = "AAAAAAAAAAAAAAAAAAAAAA";

	@Autowired
	MockMvc mvc;

	@MockitoBean
	PathRepository repository;

	@MockitoBean
	OpenAlexClient openAlex;

	@Test
	void databaseUnavailableIs503WithoutDriverDetails() throws Exception {
		when(repository.findByPublicId(anyString())).thenThrow(new CannotGetJdbcConnectionException(
				"Failed to obtain JDBC Connection: Connection to localhost:5434 refused (password=hunter2)"));
		mvc.perform(get("/api/paths/" + ID))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.title").value("Temporarily unavailable"))
				.andExpect(content().string(not(containsString("5434"))))
				.andExpect(content().string(not(containsString("JDBC"))))
				.andExpect(content().string(not(containsString("hunter2"))));
	}

	@Test
	void unexpectedFailureIs500WithAnIncidentIdAndNoInternals() throws Exception {
		when(repository.findByPublicId(anyString())).thenThrow(new IllegalStateException("secret internal state at dev.researchpath.X"));
		mvc.perform(get("/api/paths/" + ID))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.title").value("Unexpected error"))
				.andExpect(jsonPath("$.incident").exists())
				.andExpect(content().string(not(containsString("secret"))))
				.andExpect(content().string(not(containsString("IllegalState"))))
				.andExpect(content().string(not(containsString("dev.researchpath"))));
	}

}
