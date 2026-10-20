package dev.researchpath.openalex;

import java.net.http.HttpClient;
import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class OpenAlexConfiguration {

	@Bean
	JdbcCallBudget providerCallBudget(org.springframework.jdbc.core.simple.JdbcClient jdbc, ProviderBudgetProperties budget,
			Clock clock) {
		return new JdbcCallBudget(jdbc, budget, clock);
	}

	@Bean
	OpenAlexClient openAlexClient(RestClient.Builder builder, OpenAlexProperties properties, JsonMapper jsonMapper,
			Clock clock, CallBudget budget) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				// Never follow redirects: the Bearer key must only ever reach the configured API host (M13 SSRF review).
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());

		RestClient restClient = builder.clone()
				.baseUrl(properties.baseUrl())
				.requestFactory(requestFactory)
				.build();
		return new OpenAlexClient(restClient, properties, jsonMapper, duration -> Thread.sleep(duration), clock, budget);
	}

}
