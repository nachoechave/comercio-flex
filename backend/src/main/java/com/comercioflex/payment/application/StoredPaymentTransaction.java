package com.comercioflex.payment.application;

import java.math.BigDecimal;

import com.comercioflex.payment.domain.PaymentProvider;
import com.comercioflex.payment.domain.PaymentResultStatus;

public record StoredPaymentTransaction(
	long internalId,
	long paymentIntentInternalId,
	PaymentProvider provider,
	String providerPaymentId,
	PaymentResultStatus status,
	BigDecimal amount,
	String currencyCode,
	boolean applied,
	boolean reviewRequired) {
}
