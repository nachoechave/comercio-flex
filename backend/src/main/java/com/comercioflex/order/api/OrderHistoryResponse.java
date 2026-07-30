package com.comercioflex.order.api;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.order.application.OrderHistoryEntry;
import com.comercioflex.order.domain.OrderStatus;

public record OrderHistoryResponse(
	UUID id,
	OrderStatus previousStatus,
	OrderStatus newStatus,
	String note,
	UUID actorId,
	String actorDisplayName,
	Instant createdAt) {

	static OrderHistoryResponse from(OrderHistoryEntry entry) {
		return new OrderHistoryResponse(
			entry.id(),
			entry.previousStatus(),
			entry.newStatus(),
			entry.note(),
			entry.actorId(),
			entry.actorDisplayName(),
			entry.createdAt());
	}
}
