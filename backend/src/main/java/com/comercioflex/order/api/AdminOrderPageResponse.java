package com.comercioflex.order.api;

import java.util.List;

import com.comercioflex.order.application.AdminOrderPage;

public record AdminOrderPageResponse(
	List<AdminOrderSummaryResponse> items,
	int page,
	int size,
	long totalItems,
	long totalPages) {

	static AdminOrderPageResponse from(AdminOrderPage page) {
		long pages = page.totalItems() == 0
			? 0
			: (page.totalItems() + page.size() - 1) / page.size();
		return new AdminOrderPageResponse(
			page.items().stream().map(AdminOrderSummaryResponse::from).toList(),
			page.page(),
			page.size(),
			page.totalItems(),
			pages);
	}
}
