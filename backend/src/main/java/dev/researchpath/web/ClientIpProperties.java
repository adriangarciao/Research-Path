package dev.researchpath.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the rate limiter takes the client's identity from ({@code researchpath.client-ip.*}).
 *
 * @param trustedHeader name of a header set by a trusted edge proxy that carries the client address (e.g. Railway's
 *     {@code X-Real-IP}). Empty (default) = use the TCP connection address and ignore every client-supplied header.
 *     Enable it only where the edge is known to overwrite the header; prove it with the live spoof test before launch.
 */
@ConfigurationProperties("researchpath.client-ip")
public record ClientIpProperties(@DefaultValue("") String trustedHeader) {

	public static ClientIpProperties defaults() {
		return new ClientIpProperties("");
	}

	public boolean headerTrusted() {
		return trustedHeader != null && !trustedHeader.isBlank();
	}

}
