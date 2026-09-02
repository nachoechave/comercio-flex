package com.comercioflex.payment.api;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.payment.application.QrOrderInitiation;
import com.comercioflex.payment.domain.PaymentIntentStatus;

public record QrOrderResponse(
	UUID paymentAttemptId,
	String qrData,
	Instant expiresAt,
	PaymentIntentStatus status,
	boolean replayed) {

	static QrOrderResponse from(QrOrderInitiation value) {
		return new QrOrderResponse(
			value.paymentAttemptId(), value.qrData(), value.expiresAt(),
			value.status(), value.replayed());
	}
}
