package com.comercioflex.order.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.comercioflex.order.domain.FulfillmentType;
import com.comercioflex.order.domain.GuestOrder;
import com.comercioflex.order.domain.OrderStatus;

public record GuestOrderResponse(
	UUID id,
	String number,
	OrderStatus status,
	FulfillmentType fulfillmentType,
	String customerName,
	String contactHint,
	String currencyCode,
	String subtotal,
	Instant reservationExpiresAt,
	Instant createdAt,
	List<GuestOrderItemResponse> items) {

	static GuestOrderResponse from(GuestOrder order) {
		return new GuestOrderResponse(
			order.id(),
			"ORD-%06d".formatted(order.orderNumber()),
			order.status(),
			order.fulfillmentType(),
			order.customerName(),
			contactHint(order.customerPhone(), order.customerEmail()),
			order.currencyCode(),
			order.subtotal().toPlainString(),
			order.reservationExpiresAt(),
			order.createdAt(),
			order.items().stream().map(GuestOrderItemResponse::from).toList());
	}

	private static String contactHint(String phone, String email) {
		if (email != null) {
			int separator = email.indexOf('@');
			if (separator > 0) {
				return email.charAt(0) + "***" + email.substring(separator);
			}
		}
		if (phone == null || phone.length() < 4) {
			return "***";
		}
		return "***" + phone.substring(phone.length() - 4);
	}
}
