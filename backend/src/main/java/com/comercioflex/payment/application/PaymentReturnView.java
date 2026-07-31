package com.comercioflex.payment.application;

import java.time.Instant;
import java.util.UUID;

public record PaymentReturnView(
	UUID orderId,
	long orderNumber,
	String orderStatus,
	String paymentStatus,
	boolean canRetry,
	Instant updatedAt) {
}
