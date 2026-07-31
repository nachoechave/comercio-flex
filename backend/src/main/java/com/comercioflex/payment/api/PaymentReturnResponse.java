package com.comercioflex.payment.api;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.payment.application.PaymentReturnView;

public record PaymentReturnResponse(
	UUID orderId,
	long orderNumber,
	String orderStatus,
	String paymentStatus,
	boolean canRetry,
	Instant updatedAt) {

	static PaymentReturnResponse from(PaymentReturnView value) {
		return new PaymentReturnResponse(
			value.orderId(), value.orderNumber(), value.orderStatus(), value.paymentStatus(),
			value.canRetry(), value.updatedAt());
	}
}
