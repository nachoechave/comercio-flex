package com.comercioflex.order.application;

import com.comercioflex.order.domain.OrderStatus;

public record AdminOrderSearch(
	int page,
	int size,
	String query,
	OrderStatus status) {
}
