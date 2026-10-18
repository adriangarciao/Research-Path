package dev.researchpath.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class TokenBucketLimiterTest {

	private static final long MINUTE = 60_000_000_000L;

	@Test
	void allowsTheBurstThenAsksToWaitUntilATokenRefills() {
		AtomicLong now = new AtomicLong();
		TokenBucketLimiter limiter = new TokenBucketLimiter(3, 10, 100, now::get); // one token per 6 minutes
		assertThat(limiter.tryAcquire("a")).isZero();
		assertThat(limiter.tryAcquire("a")).isZero();
		assertThat(limiter.tryAcquire("a")).isZero();
		assertThat(limiter.tryAcquire("a")).isEqualTo(360);
		assertThat(limiter.tryAcquire("b")).as("other clients are independent").isZero();

		now.addAndGet(5 * MINUTE);
		assertThat(limiter.tryAcquire("a")).isEqualTo(60);
		now.addAndGet(MINUTE);
		assertThat(limiter.tryAcquire("a")).isZero();
		assertThat(limiter.tryAcquire("a")).isPositive();
	}

	@Test
	void refusedAttemptsDoNotEarnExtraTokens() {
		AtomicLong now = new AtomicLong();
		TokenBucketLimiter limiter = new TokenBucketLimiter(1, 60, 100, now::get);
		assertThat(limiter.tryAcquire("a")).isZero();
		for (int i = 0; i < 10; i++) {
			assertThat(limiter.tryAcquire("a")).isPositive();
		}
		now.addAndGet(MINUTE);
		assertThat(limiter.tryAcquire("a")).isZero();
	}

	@Test
	void memoryIsBoundedIdleClientsAreDroppedAndAFullTableFailsClosed() {
		AtomicLong now = new AtomicLong();
		TokenBucketLimiter limiter = new TokenBucketLimiter(1, 60, 2, now::get);
		assertThat(limiter.tryAcquire("a")).isZero();
		assertThat(limiter.tryAcquire("b")).isZero();
		assertThat(limiter.tryAcquire("c")).as("table full of active clients").isPositive();
		assertThat(limiter.trackedKeys()).isEqualTo(2);

		now.addAndGet(MINUTE); // a and b refill completely and become idle
		assertThat(limiter.tryAcquire("c")).isZero();
		assertThat(limiter.trackedKeys()).isLessThanOrEqualTo(2);
	}

	@Test
	void rejectsNonPositiveSettings() {
		assertThatThrownBy(() -> new TokenBucketLimiter(0, 10, 10, System::nanoTime)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TokenBucketLimiter(1, 0, 10, System::nanoTime)).isInstanceOf(IllegalArgumentException.class);
	}

}
