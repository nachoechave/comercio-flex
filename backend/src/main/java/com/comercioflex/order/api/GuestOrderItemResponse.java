package com.comercioflex.order.api;

import java.util.UUID;
import java.util.List;

import com.comercioflex.catalog.api.VariantOptionValueResponse;
import com.comercioflex.order.domain.GuestOrderItem;

public record GuestOrderItemResponse(
	UUID productId,
	UUID variantId,
	String productName,
	String size,
	String color,
	List<VariantOptionValueResponse> options,
	String unitCode,
	String unitPrice,
	String quantity,
	String lineTotal) {

	static GuestOrderItemResponse from(GuestOrderItem item) {
		return new GuestOrderItemResponse(
			item.productId(),
			item.variantId(),
			item.productName(),
			item.size(),
			item.color(),
			item.options().stream().map(VariantOptionValueResponse::from).toList(),
			item.unitCode(),
			item.unitPrice().toPlainString(),
			item.quantity().toPlainString(),
			item.lineTotal().toPlainString());
	}
}
