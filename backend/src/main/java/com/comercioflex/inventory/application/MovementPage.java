package com.comercioflex.inventory.application;

import java.util.List;

import com.comercioflex.inventory.domain.InventoryMovement;

public record MovementPage(
	List<InventoryMovement> items,
	int page,
	int size,
	long totalItems) {

	public long totalPages() {
		return totalItems == 0 ? 0 : (totalItems + size - 1) / size;
	}
}
