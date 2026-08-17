package com.comercioflex.order.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.comercioflex.catalog.domain.VariantOptionValue;

public record LockedOrderVariant(
		long internalId,
		UUID productId,
		UUID variantId,
		String productName,
		String sku,
		String size,
		String color,
		List<VariantOptionValue> options,
		BigDecimal unitPrice,
		BigDecimal physicalQuantity,
		BigDecimal reservedQuantity,
		boolean sellable) {
}

