package com.comercioflex.payment.application;

import java.math.BigDecimal;
import java.time.Instant;

import com.comercioflex.payment.domain.PaymentResultStatus;

public record VerifiedProviderPayment(
	String providerPaymentId,
	String sellerAccountId,
	String preferenceId,
	String externalReference,
	BigDecimal amount,
	String currencyCode,
	boolean liveMode,
	PaymentResultStatus status,
	Instant providerUpdatedAt) {
}
