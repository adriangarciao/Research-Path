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
	OpenAlexClient openAlexClient(RestClient.Builder builder, OpenAlexProperties properties, JsonMapper jsonMapper,
			Clock clock) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());

		RestClient restClient = builder.clone()
				.baseUrl(properties.baseUrl())
				.requestFactory(requestFactory)
				.build();
		return new OpenAlexClient(restClient, properties, jsonMapper, duration -> Thread.sleep(duration), clock);
	}

}
