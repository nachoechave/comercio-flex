package com.comercioflex.inventory.api;

import com.comercioflex.inventory.application.AdjustmentResult;

public record AdjustmentResponse(
	InventoryItemResponse inventory,
	MovementResponse movement) {

	static AdjustmentResponse from(AdjustmentResult result) {
		return new AdjustmentResponse(
			InventoryItemResponse.from(result.inventory()),
			MovementResponse.from(result.movement()));
	}
}
