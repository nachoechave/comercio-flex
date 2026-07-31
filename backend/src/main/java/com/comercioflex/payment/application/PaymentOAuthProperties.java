package com.comercioflex.payment.application;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.comercioflex.payment.domain.PaymentEnvironment;

@ConfigurationProperties("app.payments.mercado-pago")
public record PaymentOAuthProperties(
	boolean enabled,
	PaymentEnvironment environment,
	String clientId,
	String clientSecret,
	URI redirectUri,
	URI authorizationBaseUri,
	URI apiBaseUri,
	URI identityBaseUri,
	URI frontendBaseUri,
	Duration connectTimeout,
	Duration readTimeout,
	String activeKeyId,
	String encryptionKey) {

	public PaymentOAuthProperties {
		environment = environment == null ? PaymentEnvironment.TEST : environment;
		authorizationBaseUri = authorizationBaseUri == null
			? URI.create("https://auth.mercadopago.com") : authorizationBaseUri;
		apiBaseUri = apiBaseUri == null
			? URI.create("https://api.mercadopago.com") : apiBaseUri;
		identityBaseUri = identityBaseUri == null
			? URI.create("https://api.mercadolibre.com") : identityBaseUri;
		frontendBaseUri = frontendBaseUri == null
			? URI.create("http://localhost:4200") : frontendBaseUri;
		connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
		readTimeout = readTimeout == null ? Duration.ofSeconds(8) : readTimeout;
		activeKeyId = activeKeyId == null || activeKeyId.isBlank() ? "v1" : activeKeyId;
	}
}
