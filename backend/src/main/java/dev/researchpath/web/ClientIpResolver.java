package dev.researchpath.web;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The client identity used for rate limiting. By default the TCP connection address. With a trusted header
 * configured, exactly one value of that header that is a plain IPv4 or IPv6 literal; anything else (missing,
 * repeated, comma-separated, with a port or zone, a host name, oversized) is treated as unknown so the caller can
 * refuse the request. {@code X-Forwarded-For} and {@code Forwarded} are never consulted. No DNS lookups.
 */
final class ClientIpResolver {

	private static final Pattern IPV4 = Pattern
			.compile("^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");

	/** Hex groups, colons and an optional embedded IPv4 tail; no zone ids, brackets or ports. */
	private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

	private final ClientIpProperties properties;

	ClientIpResolver(ClientIpProperties properties) {
		this.properties = properties;
	}

	/** Empty when a trusted header is configured but does not carry exactly one valid address. */
	Optional<String> resolve(HttpServletRequest request) {
		if (!properties.headerTrusted()) {
			return Optional.ofNullable(request.getRemoteAddr());
		}
		List<String> values = Collections.list(request.getHeaders(properties.trustedHeader()));
		if (values.size() != 1) {
			return Optional.empty();
		}
		return parse(values.get(0).strip());
	}

	static Optional<String> parse(String value) {
		if (value == null || value.isEmpty() || value.length() > 45) {
			return Optional.empty();
		}
		if (IPV4.matcher(value).matches()) {
			return Optional.of(value);
		}
		if (value.indexOf(':') >= 0 && IPV6_CHARS.matcher(value).matches()) {
			try {
				// A string containing ':' is parsed as an IPv6 literal by the JDK; it never triggers a DNS lookup.
				java.net.InetAddress address = java.net.InetAddress.getByName(value);
				return address instanceof java.net.Inet6Address || address instanceof java.net.Inet4Address
						? Optional.of(address.getHostAddress())
						: Optional.empty();
			} catch (java.net.UnknownHostException e) {
				return Optional.empty();
			}
		}
		return Optional.empty();
	}

}
