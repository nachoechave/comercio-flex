package com.comercioflex.dashboard.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DashboardSummary(
	String currencyCode,
	String timezone,
	BigDecimal lowStockThreshold,
	BigDecimal salesToday,
	BigDecimal salesThisMonth,
	long openOrders,
	long lowStockVariants,
	List<LowStockVariant> criticalStock,
	Instant generatedAt) {

	public DashboardSummary {
		criticalStock = List.copyOf(criticalStock);
	}
}
