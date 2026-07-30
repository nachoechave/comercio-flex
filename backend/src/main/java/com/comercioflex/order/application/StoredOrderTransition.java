package com.comercioflex.order.application;

import java.util.UUID;

import com.comercioflex.order.domain.OrderStatus;

public record StoredOrderTransition(
	UUID orderId,
	OrderStatus targetStatus,
	String note) {
}
