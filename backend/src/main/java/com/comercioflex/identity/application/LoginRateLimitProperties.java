package com.comercioflex.identity.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security.login-rate-limit")
public record LoginRateLimitProperties(
	int maxAttempts,
	Duration window,
	int maxKeys) {

	public LoginRateLimitProperties {
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("max-attempts must be greater than zero");
		}
		if (window == null || window.isNegative() || window.isZero()) {
			throw new IllegalArgumentException("window must be greater than zero");
		}
		if (maxKeys < 1) {
			throw new IllegalArgumentException("max-keys must be greater than zero");
		}
	}
}
