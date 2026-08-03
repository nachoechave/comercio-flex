package com.comercioflex.dashboard.application;

import java.math.BigDecimal;
import java.util.List;

public record DashboardMetrics(
	BigDecimal salesToday,
	BigDecimal salesThisMonth,
	long openOrders,
	long lowStockVariants,
	List<LowStockVariant> criticalStock) {

	public DashboardMetrics {
		criticalStock = List.copyOf(criticalStock);
	}
}
