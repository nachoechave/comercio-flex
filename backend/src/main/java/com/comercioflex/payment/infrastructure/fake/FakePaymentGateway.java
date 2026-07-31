package com.comercioflex.payment.infrastructure.fake;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import com.comercioflex.payment.application.GatewayPayment;
import com.comercioflex.payment.application.GatewayPaymentRequest;
import com.comercioflex.payment.application.PaymentGateway;
import com.comercioflex.payment.domain.PaymentProvider;
import com.comercioflex.payment.domain.PaymentResultStatus;

public final class FakePaymentGateway implements PaymentGateway {

	private final PaymentResultStatus result;

	public FakePaymentGateway(PaymentResultStatus result) {
		this.result = Objects.requireNonNull(result);
	}

	@Override
	public PaymentProvider provider() {
		return PaymentProvider.FAKE;
	}

	@Override
	public GatewayPayment createPayment(GatewayPaymentRequest request) {
		UUID deterministicId = UUID.nameUUIDFromBytes(
			("fake-payment:" + request.paymentIntentId())
				.getBytes(StandardCharsets.UTF_8));
		return new GatewayPayment(
			"fake-" + deterministicId,
			result,
			request.amount(),
			request.currencyCode());
	}
}
