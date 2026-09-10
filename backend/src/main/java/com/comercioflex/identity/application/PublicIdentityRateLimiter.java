package com.comercioflex.identity.application;

import java.time.Duration;
import org.springframework.stereotype.Component;

/** Bounded, process-local protection, independent of administrative login limits. */
@Component
public class PublicIdentityRateLimiter {

	private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(
		new LoginRateLimitProperties(10, Duration.ofMinutes(15), 10_000));

	public synchronized void acquire(String action, String address, String email) {
		limiter.checkAllowed(action + ":ip", address);
		limiter.checkAllowed(action + ":email", email);
		limiter.recordFailure(action + ":ip", address);
		limiter.recordFailure(action + ":email", email);
	}
}
