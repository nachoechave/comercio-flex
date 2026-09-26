package com.comercioflex.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

import com.comercioflex.analytics.application.AnalyticsRepository.AnalyticsMetrics;
import com.comercioflex.analytics.application.AnalyticsRepository.AnalyticsProduct;
import com.comercioflex.analytics.application.AnalyticsRepository.AnalyticsSource;
import com.comercioflex.dashboard.application.DashboardRepository;
import com.comercioflex.dashboard.application.DashboardSettings;

class AnalyticsServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-25T23:30:00Z");
    private AnalyticsRepository repository;
    private DashboardRepository dashboardRepository;
    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        repository = mock(AnalyticsRepository.class);
        dashboardRepository = mock(DashboardRepository.class);
        service = new AnalyticsService(
                repository,
                dashboardRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        when(dashboardRepository.findSettings()).thenReturn(new DashboardSettings(
                "ARS",
                "America/Argentina/Buenos_Aires",
                new BigDecimal("5.000")));
        when(repository.findMetrics(any(), any())).thenReturn(new AnalyticsMetrics(
                200,
                140,
                480,
                150,
                45,
                20,
                8));
        when(repository.findTopProducts(any(), any(), anyInt())).thenReturn(List.of(
                new AnalyticsProduct(
                        java.util.UUID.fromString("11111111-1111-4111-8111-111111111111"),
                        "Remera",
                        90,
                        20)));
        when(repository.findTopSources(any(), any(), anyInt())).thenReturn(List.of(
                new AnalyticsSource("Instagram", 80)));
    }

    @Test
    void calculatesPeriodInTenantTimezoneAndConversion() {
        AnalyticsService.Summary summary = service.summary(7);

        ArgumentCaptor<Instant> start = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> end = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findMetrics(start.capture(), end.capture());

        assertThat(start.getValue()).isEqualTo("2026-09-19T03:00:00Z");
        assertThat(end.getValue()).isEqualTo("2026-09-26T03:00:00Z");
        assertThat(summary.visits()).isEqualTo(200);
        assertThat(summary.visitors()).isEqualTo(140);
        assertThat(summary.purchases()).isEqualTo(8);
        assertThat(summary.conversionRate()).isEqualByComparingTo("4.00");
        assertThat(summary.topProducts()).hasSize(1);
        assertThat(summary.trafficSources()).hasSize(1);
        assertThat(summary.generatedAt()).isEqualTo(NOW);
    }

    @Test
    void returnsZeroConversionWithoutVisits() {
        when(repository.findMetrics(any(), any())).thenReturn(new AnalyticsMetrics(
                0, 0, 0, 0, 0, 0, 0));

        AnalyticsService.Summary summary = service.summary(1);

        assertThat(summary.conversionRate()).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsUnsupportedPeriods() {
        assertThatThrownBy(() -> service.summary(14))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1, 7 o 30");
    }
}
