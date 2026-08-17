package com.comercioflex.order.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.comercioflex.catalog.domain.VariantOptionValue;

public record GuestOrderItem(
		UUID productId,
		UUID variantId,
		String productName,
		String size,
		String color,
		List<VariantOptionValue> options,
		String unitCode,
		BigDecimal unitPrice,
		BigDecimal quantity,
		BigDecimal lineTotal) {
}

