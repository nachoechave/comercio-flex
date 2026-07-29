package com.comercioflex.inventory.application;

import java.math.BigDecimal;

import com.comercioflex.inventory.domain.AdjustmentDirection;
import com.comercioflex.inventory.domain.InventoryMovement;
import com.comercioflex.inventory.domain.InventoryReason;

public record StoredAdjustment(
	long variantInternalId,
	AdjustmentDirection direction,
	BigDecimal quantity,
	InventoryReason reason,
	String note,
	InventoryMovement movement) {
}
