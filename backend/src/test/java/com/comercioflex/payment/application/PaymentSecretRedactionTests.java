package com.comercioflex.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.comercioflex.payment.domain.PaymentEnvironment;

class PaymentSecretRedactionTests {

	@Test
	void secretBearingValueObjectsNeverRenderTokensInDiagnosticText() {
		PaymentCredential credential = new PaymentCredential(
			"access-secret-value", "seller-123", PaymentEnvironment.PRODUCTION,
			PaymentCredential.Source.TENANT_OAUTH);
		OAuthTokenResponse response = new OAuthTokenResponse(
			"access-secret-value", "refresh-secret-value", "Bearer",
			Duration.ofHours(1), Set.of("read", "write"), "seller-123", true);

		assertThat(credential.toString())
			.contains("<redacted>")
			.doesNotContain("access-secret-value");
		assertThat(response.toString())
			.contains("<redacted>")
			.doesNotContain("access-secret-value", "refresh-secret-value");
	}
}
