package com.comercioflex.dashboard.application;

import java.math.BigDecimal;
import java.util.UUID;

public record LowStockVariant(
	UUID variantId,
	String productName,
	String sku,
	String size,
	String color,
	BigDecimal quantity) {
}
