package com.comercioflex.payment.infrastructure.fake;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.comercioflex.payment.application.GatewayPaymentRequest;
import com.comercioflex.payment.domain.PaymentResultStatus;

class FakePaymentGatewayTests {

	@ParameterizedTest
	@EnumSource(PaymentResultStatus.class)
	void returnsEachConfiguredScenarioDeterministically(PaymentResultStatus status) {
		UUID intentId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
		var request = new GatewayPaymentRequest(
			intentId,
			intentId.toString(),
			new BigDecimal("1500.00"),
			"ARS");
		FakePaymentGateway gateway = new FakePaymentGateway(status);

		var first = gateway.createPayment(request);
		var second = gateway.createPayment(request);

		assertThat(first).isEqualTo(second);
		assertThat(first.status()).isEqualTo(status);
		assertThat(first.amount()).isEqualByComparingTo("1500.00");
		assertThat(first.currencyCode()).isEqualTo("ARS");
	}
}
