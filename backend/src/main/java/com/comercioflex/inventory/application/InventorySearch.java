package com.comercioflex.inventory.application;

import com.comercioflex.inventory.domain.InventoryAvailability;

public record InventorySearch(
	int page,
	int size,
	String query,
	InventoryAvailability availability) {
}
