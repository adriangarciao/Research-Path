package dev.researchpath.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Exact-origin CORS for the path API (release decision R2: the Vercel-hosted UI calls the Railway API directly).
 * Nothing is registered when no origin is configured, so the API stays same-origin only.
 */
@Configuration(proxyBeanMethods = false)
class CorsConfiguration implements WebMvcConfigurer {

	private final CorsProperties properties;

	CorsConfiguration(org.springframework.beans.factory.ObjectProvider<CorsProperties> properties) {
		this.properties = properties.getIfAvailable(() -> new CorsProperties(java.util.List.of()));
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		if (properties.allowedOrigins().isEmpty()) {
			return;
		}
		registry.addMapping("/api/paths/**")
				.allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
				.allowedMethods("GET", "POST")
				.allowedHeaders("Content-Type", "Idempotency-Key")
				.exposedHeaders("Location", "Retry-After")
				.allowCredentials(false)
				.maxAge(600);
		registry.addMapping("/api/paths")
				.allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
				.allowedMethods("POST")
				.allowedHeaders("Content-Type", "Idempotency-Key")
				.exposedHeaders("Location", "Retry-After")
				.allowCredentials(false)
				.maxAge(600);
	}

}
