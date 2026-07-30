package com.comercioflex.order.application;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.order.domain.OrderStatus;

public record OrderHistoryEntry(
	UUID id,
	OrderStatus previousStatus,
	OrderStatus newStatus,
	String note,
	UUID actorId,
	String actorDisplayName,
	Instant createdAt) {
}
