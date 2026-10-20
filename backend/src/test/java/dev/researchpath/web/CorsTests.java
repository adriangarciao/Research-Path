package dev.researchpath.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.researchpath.TestcontainersConfiguration;
import dev.researchpath.openalex.OpenAlexClient;

/** Exact-origin CORS: the configured UI origin works, everything else gets no CORS grant. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "researchpath.cors.allowed-origins=https://researchpath.vercel.app")
@AutoConfigureMockMvc
class CorsTests {

	private static final String UI = "https://researchpath.vercel.app";
	private static final String PATH = "/api/paths/AAAAAAAAAAAAAAAAAAAAAA";

	@Autowired
	MockMvc mvc;

	@MockitoBean
	OpenAlexClient openAlex;

	@Test
	void allowedOriginPassesPreflightWithOnlyTheNeededMethodsAndHeaders() throws Exception {
		mvc.perform(options("/api/paths").header("Origin", UI).header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "content-type,idempotency-key"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", UI))
				.andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
		mvc.perform(options(PATH + "/revisions").header("Origin", UI).header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "content-type"))
				.andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", UI));
		mvc.perform(get(PATH).header("Origin", UI))
				.andExpect(header().string("Access-Control-Allow-Origin", UI))
				.andExpect(header().string("Access-Control-Expose-Headers", org.hamcrest.Matchers.containsString("Retry-After")));
	}

	@Test
	void otherOriginsMethodsAndHeadersGetNoGrant() throws Exception {
		for (String evil : List.of("https://evil.example", "https://researchpath.vercel.app.evil.example",
				"http://researchpath.vercel.app", "null")) {
			mvc.perform(options("/api/paths").header("Origin", evil).header("Access-Control-Request-Method", "POST"))
					.andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
		}
		mvc.perform(options(PATH).header("Origin", UI).header("Access-Control-Request-Method", "DELETE"))
				.andExpect(status().isForbidden());
		mvc.perform(options("/api/paths").header("Origin", UI).header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "authorization")).andExpect(status().isForbidden());
		mvc.perform(options("/api/dev/snapshots").header("Origin", UI).header("Access-Control-Request-Method", "POST"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
		mvc.perform(options("/actuator/health").header("Origin", UI).header("Access-Control-Request-Method", "GET"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@Test
	void originsMustBeExact() {
		assertThat(new CorsProperties(List.of(" https://app.example.com ", "http://localhost:5173", "")).allowedOrigins())
				.containsExactly("https://app.example.com", "http://localhost:5173");
		for (String bad : List.of("*", "https://*.vercel.app", "https://app.example.com/", "https://app.example.com/path",
				"http://app.example.com", "app.example.com", "https://app.example.com?x=1", "null")) {
			assertThatThrownBy(() -> new CorsProperties(List.of(bad))).as(bad).isInstanceOf(IllegalArgumentException.class);
		}
	}

}
