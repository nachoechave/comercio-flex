package com.comercioflex.payment.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentIntent(
	UUID id,
	UUID orderId,
	PaymentProvider provider,
	PaymentIntentStatus status,
	int attemptNumber,
	BigDecimal amount,
	String currencyCode,
	String externalReference) {
}
