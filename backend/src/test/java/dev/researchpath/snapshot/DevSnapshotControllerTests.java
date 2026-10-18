package dev.researchpath.snapshot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.researchpath.openalex.OpenAlexException;

@WebMvcTest(controllers = DevSnapshotController.class,
		properties = { "researchpath.dev-api.enabled=true", "spring.mvc.problemdetails.enabled=true" })
class DevSnapshotControllerTests {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	CandidateSnapshotService service;

	private static SnapshotSummary summary(UUID id) {
		return new SnapshotSummary(id, "graph neural networks", Instant.parse("2026-10-17T12:00:00Z"), "OPENALEX", 10,
				964424L, 10, 9, 1, 1, new SnapshotSummary.Availability(8, 9, 9, 9, 7, 9, 9, 9, 9, 0),
				new SnapshotSummary.Classification(3, 40, "Advanced Graph Neural Networks", 7), 5, 1,
				new SnapshotSummary.Identity(1, 1, 0, 0, 0, 8, 1), WorkJson.JSON.readTree("[]"));
	}

	@Test
	void createsSnapshot() throws Exception {
		UUID id = UUID.randomUUID();
		when(service.createSnapshot("graph neural networks", 10)).thenReturn(summary(id));

		mvc.perform(post("/api/dev/snapshots").contentType(MediaType.APPLICATION_JSON)
				.content("{\"topic\": \"graph neural networks\", \"maxCandidates\": 10}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.storedCount").value(9))
				.andExpect(jsonPath("$.availability.withAbstract").value(8))
				.andExpect(jsonPath("$.classification.mostCommonPrimaryTopicCount").value(7))
				.andExpect(jsonPath("$.internalCitationEdges").value(5));
	}

	@Test
	void rejectsBlankTopicAndOutOfRangeCount() throws Exception {
		mvc.perform(post("/api/dev/snapshots").contentType(MediaType.APPLICATION_JSON).content("{\"topic\": \"  \"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/dev/snapshots").contentType(MediaType.APPLICATION_JSON)
				.content("{\"topic\": \"t\", \"maxCandidates\": 500}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/dev/snapshots").contentType(MediaType.APPLICATION_JSON)
				.content("{\"topic\": \"" + "x".repeat(301) + "\"}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@Test
	void rateLimitMapsTo503AndOtherProviderFailuresTo502() throws Exception {
		when(service.createSnapshot(anyString(), any())).thenThrow(new OpenAlexException("HTTP 429", 429, null));
		mvc.perform(post("/api/dev/snapshots").contentType(MediaType.APPLICATION_JSON).content("{\"topic\": \"t\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.title").value("Research data provider unavailable"))
				.andExpect(jsonPath("$.providerStatus").value(429));

		doThrow(new OpenAlexException("network", null, null)).when(service).createSnapshot(anyString(), any());
		mvc.perform(post("/api/dev/snapshots").contentType(MediaType.APPLICATION_JSON).content("{\"topic\": \"t\"}"))
				.andExpect(status().isBadGateway());
	}

	@Test
	void getReturns404ForUnknownAnd400ForMalformedId() throws Exception {
		when(service.findSummary(any())).thenReturn(Optional.empty());
		mvc.perform(get("/api/dev/snapshots/" + UUID.randomUUID())).andExpect(status().isNotFound());
		mvc.perform(get("/api/dev/snapshots/not-a-uuid")).andExpect(status().isBadRequest());
	}

}
