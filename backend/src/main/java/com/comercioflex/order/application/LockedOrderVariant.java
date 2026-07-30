package com.comercioflex.order.application;

import java.math.BigDecimal;
import java.util.UUID;

public record LockedOrderVariant(
		long internalId,
		UUID productId,
		UUID variantId,
		String productName,
		String sku,
		String size,
		String color,
		BigDecimal unitPrice,
		BigDecimal physicalQuantity,
		BigDecimal reservedQuantity,
		boolean sellable) {
}

