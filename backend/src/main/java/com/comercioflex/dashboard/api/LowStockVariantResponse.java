package com.comercioflex.dashboard.api;

import java.util.UUID;

import com.comercioflex.dashboard.application.LowStockVariant;

public record LowStockVariantResponse(
	UUID variantId,
	String productName,
	String sku,
	String size,
	String color,
	String quantity) {

	static LowStockVariantResponse from(LowStockVariant variant) {
		return new LowStockVariantResponse(
			variant.variantId(),
			variant.productName(),
			variant.sku(),
			variant.size(),
			variant.color(),
			variant.quantity().setScale(3).toPlainString());
	}
}
