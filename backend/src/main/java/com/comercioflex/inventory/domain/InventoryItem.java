package com.comercioflex.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.comercioflex.catalog.domain.ProductStatus;

public record InventoryItem(
	UUID variantId,
	UUID productId,
	String productName,
	ProductStatus productStatus,
	String sku,
	String size,
	String color,
	boolean variantActive,
	BigDecimal quantity,
	long version,
	Instant updatedAt) {
}
