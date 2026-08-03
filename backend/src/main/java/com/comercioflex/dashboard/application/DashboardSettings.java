package com.comercioflex.dashboard.application;

import java.math.BigDecimal;

public record DashboardSettings(
	String currencyCode,
	String timezone,
	BigDecimal lowStockThreshold) {
}
