package com.comercioflex.inventory.application;

import com.comercioflex.inventory.domain.InventoryItem;
import com.comercioflex.inventory.domain.InventoryMovement;

public record AdjustmentResult(
	InventoryItem inventory,
	InventoryMovement movement,
	boolean replay) {
}
