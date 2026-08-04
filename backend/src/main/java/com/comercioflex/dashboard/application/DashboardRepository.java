package com.comercioflex.dashboard.application;

import java.math.BigDecimal;
import java.time.Instant;

public interface DashboardRepository {

	DashboardSettings findSettings();

	DashboardMetrics findMetrics(
		Instant dayStart,
		Instant nextDayStart,
		Instant monthStart,
		Instant nextMonthStart,
		BigDecimal lowStockThreshold,
		int criticalStockLimit);

	void updateLowStockThreshold(BigDecimal threshold);
}
