package dev.researchpath.web;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Browser origins allowed to call the API cross-origin ({@code researchpath.cors.allowed-origins}). Empty (default) =
 * same-origin only. Each entry must be an exact origin — {@code https://host[:port]}, or {@code http://localhost[:port]}
 * / {@code http://127.0.0.1[:port]} for local development — with no wildcard, path, query or trailing slash. Anything
 * else stops startup, so a typo can never widen access.
 */
@ConfigurationProperties("researchpath.cors")
public record CorsProperties(@DefaultValue({}) List<String> allowedOrigins) {

	private static final Pattern HTTPS_ORIGIN = Pattern.compile("^https://[A-Za-z0-9.-]+(:\\d{1,5})?$");
	private static final Pattern LOCAL_ORIGIN = Pattern.compile("^http://(localhost|127\\.0\\.0\\.1)(:\\d{1,5})?$");

	public CorsProperties {
		allowedOrigins = allowedOrigins == null ? List.of()
				: allowedOrigins.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
		for (String origin : allowedOrigins) {
			if (!(HTTPS_ORIGIN.matcher(origin).matches() || LOCAL_ORIGIN.matcher(origin).matches())
					|| URI.create(origin).getHost() == null) {
				throw new IllegalArgumentException("researchpath.cors.allowed-origins must list exact origins such as "
						+ "https://app.example.com (no wildcard, path or trailing slash); got: " + origin);
			}
		}
	}

}
