package com.comercioflex.payment.application;

import java.math.BigDecimal;
import java.util.UUID;

public record GatewayPaymentRequest(
	UUID paymentIntentId,
	String externalReference,
	BigDecimal amount,
	String currencyCode) {
}
