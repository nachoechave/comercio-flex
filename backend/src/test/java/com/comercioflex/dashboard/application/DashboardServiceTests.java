package com.comercioflex.dashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DashboardServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-03T02:30:00Z");
	private DashboardRepository repository;
	private DashboardService service;

	@BeforeEach
	void setUp() {
		repository = mock(DashboardRepository.class);
		service = new DashboardService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
		when(repository.findSettings()).thenReturn(new DashboardSettings(
			"ARS",
			"America/Argentina/Buenos_Aires",
			new BigDecimal("5.000")));
		when(repository.findMetrics(any(), any(), any(), any(), any(), anyInt()))
			.thenReturn(new DashboardMetrics(
				new BigDecimal("1200.00"),
				new BigDecimal("8600.00"),
				2,
				1,
				List.of()));
	}

	@Test
	void calculatesDayAndMonthBoundariesInStoreTimezone() {
		DashboardSummary summary = service.findSummary();

		ArgumentCaptor<Instant> dayStart = ArgumentCaptor.forClass(Instant.class);
		ArgumentCaptor<Instant> nextDayStart = ArgumentCaptor.forClass(Instant.class);
		ArgumentCaptor<Instant> monthStart = ArgumentCaptor.forClass(Instant.class);
		ArgumentCaptor<Instant> nextMonthStart = ArgumentCaptor.forClass(Instant.class);
		verify(repository).findMetrics(
			dayStart.capture(),
			nextDayStart.capture(),
			monthStart.capture(),
			nextMonthStart.capture(),
			eq(new BigDecimal("5.000")),
			eq(5));

		assertThat(dayStart.getValue()).isEqualTo("2026-08-02T03:00:00Z");
		assertThat(nextDayStart.getValue()).isEqualTo("2026-08-03T03:00:00Z");
		assertThat(monthStart.getValue()).isEqualTo("2026-08-01T03:00:00Z");
		assertThat(nextMonthStart.getValue()).isEqualTo("2026-09-01T03:00:00Z");
		assertThat(summary.generatedAt()).isEqualTo(NOW);
		assertThat(summary.salesToday()).isEqualByComparingTo("1200.00");
	}

	@Test
	void updatesThresholdAndReturnsFreshSummary() {
		DashboardSummary summary = service.updateLowStockThreshold(
			new BigDecimal("2.500"));

		verify(repository).updateLowStockThreshold(new BigDecimal("2.500"));
		verify(repository).findSettings();
		assertThat(summary.lowStockThreshold()).isEqualByComparingTo("5.000");
	}
}
