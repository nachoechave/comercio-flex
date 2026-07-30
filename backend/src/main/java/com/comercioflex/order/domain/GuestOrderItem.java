package com.comercioflex.order.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record GuestOrderItem(
		UUID productId,
		UUID variantId,
		String productName,
		String size,
		String color,
		String unitCode,
		BigDecimal unitPrice,
		BigDecimal quantity,
		BigDecimal lineTotal) {
}

