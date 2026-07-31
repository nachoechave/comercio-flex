package com.comercioflex.payment.application;

import com.comercioflex.payment.domain.PaymentProvider;

public interface PaymentGateway {

	PaymentProvider provider();

	GatewayPayment createPayment(GatewayPaymentRequest request);
}
