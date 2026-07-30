package com.comercioflex.order.application;

import java.util.List;
import java.util.UUID;

public record CreateGuestOrderCommand(
		UUID idempotencyKey,
		String customerName,
		String customerPhone,
		String customerEmail,
		String notes,
		List<OrderItemCommand> items) {
}

