package com.comercioflex.dashboard.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

	private static final int CRITICAL_STOCK_LIMIT = 5;
	private final DashboardRepository repository;
	private final Clock clock;

	@Autowired
	public DashboardService(DashboardRepository repository) {
		this(repository, Clock.systemUTC());
	}

	DashboardService(DashboardRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public DashboardSummary findSummary() {
		DashboardSettings settings = repository.findSettings();
		ZoneId zone = ZoneId.of(settings.timezone());
		Instant now = clock.instant();
		LocalDate today = now.atZone(zone).toLocalDate();
		Instant dayStart = today.atStartOfDay(zone).toInstant();
		Instant nextDayStart = today.plusDays(1).atStartOfDay(zone).toInstant();
		Instant monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant();
		Instant nextMonthStart = today.withDayOfMonth(1).plusMonths(1)
			.atStartOfDay(zone).toInstant();
		DashboardMetrics metrics = repository.findMetrics(
			dayStart,
			nextDayStart,
			monthStart,
			nextMonthStart,
			settings.lowStockThreshold(),
			CRITICAL_STOCK_LIMIT);
		return new DashboardSummary(
			settings.currencyCode(),
			settings.timezone(),
			settings.lowStockThreshold(),
			metrics.salesToday(),
			metrics.salesThisMonth(),
			metrics.openOrders(),
			metrics.lowStockVariants(),
			metrics.criticalStock(),
			now);
	}

	public DashboardSummary updateLowStockThreshold(BigDecimal threshold) {
		repository.updateLowStockThreshold(threshold);
		return findSummary();
	}
}
