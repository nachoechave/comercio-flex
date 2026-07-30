package com.comercioflex.order.api;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.order.application.AdminOrderSummary;
import com.comercioflex.order.domain.FulfillmentType;
import com.comercioflex.order.domain.OrderStatus;

public record AdminOrderSummaryResponse(
	UUID id,
	String number,
	OrderStatus status,
	FulfillmentType fulfillmentType,
	String customerName,
	String customerPhone,
	String currencyCode,
	String subtotal,
	Instant createdAt) {

	static AdminOrderSummaryResponse from(AdminOrderSummary order) {
		return new AdminOrderSummaryResponse(
			order.id(),
			"ORD-%06d".formatted(order.number()),
			order.status(),
			order.fulfillmentType(),
			order.customerName(),
			order.customerPhone(),
			order.currencyCode(),
			order.subtotal().toPlainString(),
			order.createdAt());
	}
}
