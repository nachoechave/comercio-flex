package com.comercioflex.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryMovement(
	UUID id,
	AdjustmentDirection direction,
	BigDecimal delta,
	BigDecimal quantityBefore,
	BigDecimal quantityAfter,
	InventoryReason reason,
	String note,
	InventoryActor actor,
	Instant createdAt) {
}
