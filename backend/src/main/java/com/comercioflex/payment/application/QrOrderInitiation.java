package com.comercioflex.payment.application;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.payment.domain.PaymentIntentStatus;

public record QrOrderInitiation(
	UUID paymentAttemptId,
	String qrData,
	Instant expiresAt,
	PaymentIntentStatus status,
	boolean replayed) {
}
