package com.comercioflex.payment.application;

import java.util.UUID;

public record PaymentCommand(
	UUID orderId,
	UUID idempotencyKey) {
}
