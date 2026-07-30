package com.comercioflex.order.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.comercioflex.order.application.AdminOrderDetail;
import com.comercioflex.order.domain.FulfillmentType;
import com.comercioflex.order.domain.OrderStatus;

public record AdminOrderDetailResponse(
	UUID id,
	String number,
	OrderStatus status,
	FulfillmentType fulfillmentType,
	String customerName,
	String customerPhone,
	String customerEmail,
	String notes,
	String currencyCode,
	String subtotal,
	Instant reservationExpiresAt,
	Instant createdAt,
	long version,
	List<GuestOrderItemResponse> items,
	List<OrderHistoryResponse> history) {

	static AdminOrderDetailResponse from(AdminOrderDetail order) {
		return new AdminOrderDetailResponse(
			order.id(),
			"ORD-%06d".formatted(order.number()),
			order.status(),
			order.fulfillmentType(),
			order.customerName(),
			order.customerPhone(),
			order.customerEmail(),
			order.notes(),
			order.currencyCode(),
			order.subtotal().toPlainString(),
			order.reservationExpiresAt(),
			order.createdAt(),
			order.version(),
			order.items().stream().map(GuestOrderItemResponse::from).toList(),
			order.history().stream().map(OrderHistoryResponse::from).toList());
	}
}
