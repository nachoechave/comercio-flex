package com.comercioflex.inventory.api;

import java.util.List;

import com.comercioflex.inventory.application.InventoryPage;

public record InventoryPageResponse(
	List<InventoryItemResponse> items,
	int page,
	int size,
	long totalItems,
	long totalPages) {

	static InventoryPageResponse from(InventoryPage page) {
		return new InventoryPageResponse(
			page.items().stream().map(InventoryItemResponse::from).toList(),
			page.page(),
			page.size(),
			page.totalItems(),
			page.totalPages());
	}
}
