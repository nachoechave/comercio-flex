package com.comercioflex.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.comercioflex.catalog.domain.ProductStatus;
import com.comercioflex.catalog.domain.VariantOptionValue;

public record InventoryItem(
	UUID variantId,
	UUID productId,
	String productName,
	ProductStatus productStatus,
	String sku,
	String size,
	String color,
	List<VariantOptionValue> options,
	boolean variantActive,
	BigDecimal quantity,
	long version,
	Instant updatedAt) {
}
