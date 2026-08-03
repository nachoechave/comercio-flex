package com.comercioflex.dashboard.api;

import java.time.Instant;
import java.util.List;

import com.comercioflex.dashboard.application.DashboardSummary;

public record DashboardSummaryResponse(
	String currencyCode,
	String timezone,
	String lowStockThreshold,
	String salesToday,
	String salesThisMonth,
	long openOrders,
	long lowStockVariants,
	List<LowStockVariantResponse> criticalStock,
	Instant generatedAt) {

	static DashboardSummaryResponse from(DashboardSummary summary) {
		return new DashboardSummaryResponse(
			summary.currencyCode(),
			summary.timezone(),
			summary.lowStockThreshold().setScale(3).toPlainString(),
			summary.salesToday().setScale(2).toPlainString(),
			summary.salesThisMonth().setScale(2).toPlainString(),
			summary.openOrders(),
			summary.lowStockVariants(),
			summary.criticalStock().stream().map(LowStockVariantResponse::from).toList(),
			summary.generatedAt());
	}
}
