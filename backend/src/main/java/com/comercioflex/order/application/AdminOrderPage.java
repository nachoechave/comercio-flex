package com.comercioflex.order.application;

import java.util.List;

public record AdminOrderPage(
	List<AdminOrderSummary> items,
	int page,
	int size,
	long totalItems) {
}
