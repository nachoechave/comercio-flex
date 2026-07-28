package com.comercioflex.identity.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTests {

	private final LoginRateLimitProperties properties =
		new LoginRateLimitProperties(2, Duration.ofMinutes(15), 100);
	private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(
		properties,
		Clock.fixed(Instant.parse("2026-07-28T12:00:00Z"), ZoneOffset.UTC));

	@Test
	void limitsACombinedIpAndEmailAfterTheConfiguredFailures() {
		limiter.recordFailure("127.0.0.1", "owner@example.com");
		limiter.recordFailure("127.0.0.1", "owner@example.com");

		assertThatThrownBy(() ->
			limiter.checkAllowed("127.0.0.1", "owner@example.com"))
			.isInstanceOf(LoginRateLimitExceededException.class);
	}

	@Test
	void keepsDifferentEmailsAndIpsIndependentAndCanResetSuccess() {
		limiter.recordFailure("127.0.0.1", "owner@example.com");
		limiter.recordFailure("127.0.0.1", "owner@example.com");

		assertThatCode(() ->
			limiter.checkAllowed("127.0.0.1", "other@example.com"))
			.doesNotThrowAnyException();
		assertThatCode(() ->
			limiter.checkAllowed("127.0.0.2", "owner@example.com"))
			.doesNotThrowAnyException();

		limiter.reset("127.0.0.1", "owner@example.com");
		assertThatCode(() ->
			limiter.checkAllowed("127.0.0.1", "owner@example.com"))
			.doesNotThrowAnyException();
	}
}
