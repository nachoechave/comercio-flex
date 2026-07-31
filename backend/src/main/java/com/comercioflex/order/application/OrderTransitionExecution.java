package com.comercioflex.order.application;

public record OrderTransitionExecution(
	AdminOrderDetail detail,
	boolean expired) {

	public static OrderTransitionExecution completed(AdminOrderDetail detail) {
		return new OrderTransitionExecution(detail, false);
	}

	public static OrderTransitionExecution expiration() {
		return new OrderTransitionExecution(null, true);
	}
}
