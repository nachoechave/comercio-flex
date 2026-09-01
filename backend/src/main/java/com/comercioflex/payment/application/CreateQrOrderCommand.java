package com.comercioflex.payment.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

public record CreateQrOrderCommand(
	UUID providerIdempotencyKey,
	String externalReference,
	String externalPosId,
	BigDecimal amount,
	String currencyCode,
	Duration expiration) {
}
