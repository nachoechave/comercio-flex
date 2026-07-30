package com.comercioflex.order.application;

import java.util.UUID;

import com.comercioflex.order.domain.OrderStatus;

public record OrderTransitionCommand(
	UUID orderId,
	UUID idempotencyKey,
	OrderStatus targetStatus,
	String note,
	UUID actorId,
	String actorDisplayName) {
}
