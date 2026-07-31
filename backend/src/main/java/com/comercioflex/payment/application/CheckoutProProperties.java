package com.comercioflex.payment.application;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.payments.checkout-pro")
public record CheckoutProProperties(
	boolean enabled,
	String testAccessToken,
	String testSellerAccountId,
	String testDemoTenantSlug,
	URI publicBackendBaseUri,
	URI frontendBaseUri,
	String webhookSecret,
	Duration connectTimeout,
	Duration readTimeout,
	Duration webhookLease,
	Duration retryDelay,
	int maxWebhookAttempts,
	Duration returnTokenTtl) {

	public CheckoutProProperties {
		connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
		readTimeout = readTimeout == null ? Duration.ofSeconds(8) : readTimeout;
		webhookLease = webhookLease == null ? Duration.ofSeconds(30) : webhookLease;
		retryDelay = retryDelay == null ? Duration.ofSeconds(30) : retryDelay;
		maxWebhookAttempts = maxWebhookAttempts <= 0 ? 8 : maxWebhookAttempts;
		returnTokenTtl = returnTokenTtl == null ? Duration.ofHours(24) : returnTokenTtl;
	}
}
