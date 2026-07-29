package com.comercioflex.inventory.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.comercioflex.inventory.domain.AdjustmentDirection;
import com.comercioflex.inventory.domain.InventoryActor;
import com.comercioflex.inventory.domain.InventoryReason;

public record AdjustmentCommand(
	UUID variantId,
	UUID idempotencyKey,
	AdjustmentDirection direction,
	BigDecimal quantity,
	InventoryReason reason,
	String note,
	InventoryActor actor) {
}
