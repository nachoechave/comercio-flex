package com.comercioflex.inventory.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.comercioflex.catalog.api.VariantOptionValueResponse;
import com.comercioflex.catalog.domain.ProductStatus;
import com.comercioflex.inventory.domain.InventoryItem;

public record InventoryItemResponse(
	UUID variantId,
	UUID productId,
	String productName,
	ProductStatus productStatus,
	String sku,
	String size,
	String color,
	List<VariantOptionValueResponse> options,
	boolean variantActive,
	String quantity,
	long version,
	Instant updatedAt) {

	static InventoryItemResponse from(InventoryItem item) {
		return new InventoryItemResponse(
			item.variantId(),
			item.productId(),
			item.productName(),
			item.productStatus(),
			item.sku(),
			item.size(),
			item.color(),
			item.options().stream().map(VariantOptionValueResponse::from).toList(),
			item.variantActive(),
			DecimalQuantity.format(item.quantity()),
			item.version(),
			item.updatedAt());
	}
}
