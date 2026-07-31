package com.comercioflex.payment.application;

import java.math.BigDecimal;

import com.comercioflex.payment.domain.PaymentResultStatus;

public record GatewayPayment(
	String providerPaymentId,
	PaymentResultStatus status,
	BigDecimal amount,
	String currencyCode) {
}
