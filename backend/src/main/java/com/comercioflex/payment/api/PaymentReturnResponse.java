package com.comercioflex.payment.api;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.payment.application.PaymentReturnView;
import com.comercioflex.payment.application.PaymentReturnOutcome;

public record PaymentReturnResponse(
	UUID orderId,
	long orderNumber,
	String orderStatus,
	String paymentStatus,
	PaymentReturnOutcome returnOutcome,
	boolean canRetry,
	Instant updatedAt) {

	static PaymentReturnResponse from(PaymentReturnView value) {
		return new PaymentReturnResponse(
			value.orderId(), value.orderNumber(), value.orderStatus(), value.paymentStatus(),
			value.returnOutcome(), value.canRetry(), value.updatedAt());
	}
}
