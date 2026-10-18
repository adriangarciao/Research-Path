package dev.researchpath.web;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Per-key token buckets: {@code capacity} requests at once, refilled at {@code perHour}. Memory is bounded: when
 * {@code maxKeys} keys are tracked, idle (full) buckets are dropped; if the table is still full, new keys are refused
 * (fail closed) rather than tracked without limit.
 */
final class TokenBucketLimiter {

	private record Bucket(double tokens, long updatedNanos) {
	}

	private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
	private final double capacity;
	private final double tokensPerNano;
	private final int maxKeys;
	private final LongSupplier nanoClock;

	TokenBucketLimiter(int capacity, int perHour, int maxKeys, LongSupplier nanoClock) {
		if (capacity < 1 || perHour < 1 || maxKeys < 1) {
			throw new IllegalArgumentException("rate limit values must be positive");
		}
		this.capacity = capacity;
		this.tokensPerNano = perHour / 3_600e9;
		this.maxKeys = maxKeys;
		this.nanoClock = nanoClock;
	}

	/** Takes one token. Returns 0 if allowed, otherwise the whole seconds until a token is available (≥ 1). */
	long tryAcquire(String key) {
		long now = nanoClock.getAsLong();
		if (!buckets.containsKey(key) && buckets.size() >= maxKeys) {
			evictIdle(now);
			if (buckets.size() >= maxKeys) {
				return 60;
			}
		}
		long[] wait = {0};
		buckets.compute(key, (k, b) -> {
			double tokens = b == null ? capacity : Math.min(capacity, b.tokens() + (now - b.updatedNanos()) * tokensPerNano);
			if (tokens >= 1) {
				return new Bucket(tokens - 1, now);
			}
			wait[0] = Math.max(1, (long) Math.ceil((1 - tokens) / tokensPerNano / 1e9));
			return new Bucket(tokens, now);
		});
		return wait[0];
	}

	int trackedKeys() {
		return buckets.size();
	}

	private void evictIdle(long now) {
		buckets.entrySet().removeIf(e -> e.getValue().tokens() + (now - e.getValue().updatedNanos()) * tokensPerNano >= capacity);
	}

}
