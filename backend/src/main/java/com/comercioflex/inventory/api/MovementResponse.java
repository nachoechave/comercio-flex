package com.comercioflex.inventory.api;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.inventory.domain.AdjustmentDirection;
import com.comercioflex.inventory.domain.InventoryMovement;
import com.comercioflex.inventory.domain.InventoryReason;

public record MovementResponse(
	UUID id,
	AdjustmentDirection direction,
	String delta,
	String quantityBefore,
	String quantityAfter,
	InventoryReason reason,
	String note,
	MovementActorResponse actor,
	Instant createdAt) {

	static MovementResponse from(InventoryMovement movement) {
		return new MovementResponse(
			movement.id(),
			movement.direction(),
			DecimalQuantity.format(movement.delta()),
			DecimalQuantity.format(movement.quantityBefore()),
			DecimalQuantity.format(movement.quantityAfter()),
			movement.reason(),
			movement.note(),
			MovementActorResponse.from(movement.actor()),
			movement.createdAt());
	}
}
