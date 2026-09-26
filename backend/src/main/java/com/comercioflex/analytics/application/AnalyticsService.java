package com.comercioflex.analytics.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.comercioflex.analytics.application.AnalyticsRepository.AnalyticsEvent;
import com.comercioflex.analytics.application.AnalyticsRepository.AnalyticsProduct;
import com.comercioflex.analytics.application.AnalyticsRepository.AnalyticsSource;
import com.comercioflex.analytics.application.AnalyticsRepository.EventType;
import com.comercioflex.dashboard.application.DashboardRepository;

@Service
public class AnalyticsService {

    private static final List<Integer> ALLOWED_PERIODS = List.of(1, 7, 30);
    private static final int TOP_LIMIT = 8;

    private final AnalyticsRepository repository;
    private final DashboardRepository dashboardRepository;
    private final Clock clock;

    @Autowired
    public AnalyticsService(
            AnalyticsRepository repository,
            DashboardRepository dashboardRepository) {
        this(repository, dashboardRepository, Clock.systemUTC());
    }

    AnalyticsService(
            AnalyticsRepository repository,
            DashboardRepository dashboardRepository,
            Clock clock) {
        this.repository = repository;
        this.dashboardRepository = dashboardRepository;
        this.clock = clock;
    }

    public void record(
            EventType type,
            UUID visitorId,
            UUID sessionId,
            String path,
            String source,
            String medium,
            UUID productId) {
        String normalizedPath = normalizePath(path);
        String normalizedSource = normalizeSource(source);
        String normalizedMedium = normalizeOptional(medium, 80);
        UUID normalizedProduct = type == EventType.PRODUCT_VIEW || type == EventType.ADD_TO_CART
                ? productId
                : null;

        repository.record(new AnalyticsEvent(
                type,
                visitorId,
                sessionId,
                normalizedPath,
                normalizedSource,
                normalizedMedium,
                normalizedProduct));
    }

    public Summary summary(int days) {
        if (!ALLOWED_PERIODS.contains(days)) {
            throw new IllegalArgumentException("El período debe ser 1, 7 o 30 días.");
        }

        String timezone = dashboardRepository.findSettings().timezone();
        ZoneId zone = ZoneId.of(timezone);
        Instant now = clock.instant();
        LocalDate today = now.atZone(zone).toLocalDate();
        Instant start = today.minusDays(days - 1L).atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();

        var metrics = repository.findMetrics(start, end);
        BigDecimal conversionRate = metrics.visits() == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(metrics.purchases())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(metrics.visits()), 2, RoundingMode.HALF_UP);

        return new Summary(
                days,
                timezone,
                start,
                end,
                metrics.visits(),
                metrics.visitors(),
                metrics.pageViews(),
                metrics.productViews(),
                metrics.addToCarts(),
                metrics.checkouts(),
                metrics.purchases(),
                conversionRate,
                repository.findTopProducts(start, end, TOP_LIMIT),
                repository.findTopSources(start, end, TOP_LIMIT),
                now);
    }

    private static String normalizePath(String value) {
        if (value == null) return "/";
        String normalized = value.trim();
        if (normalized.isEmpty()) return "/";
        if (!normalized.startsWith("/")) normalized = "/" + normalized;
        return normalized.length() <= 512 ? normalized : normalized.substring(0, 512);
    }

    private static String normalizeSource(String value) {
        String normalized = normalizeOptional(value, 120);
        return normalized == null ? "Directo" : normalized;
    }

    private static String normalizeOptional(String value, int maxLength) {
        if (value == null) return null;
        String normalized = value.trim().replaceAll("[\\p{Cntrl}]", "");
        if (normalized.isEmpty()) return null;
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }

    public record Summary(
            int days,
            String timezone,
            Instant from,
            Instant to,
            long visits,
            long visitors,
            long pageViews,
            long productViews,
            long addToCarts,
            long checkouts,
            long purchases,
            BigDecimal conversionRate,
            List<AnalyticsProduct> topProducts,
            List<AnalyticsSource> trafficSources,
            Instant generatedAt) {
    }
}
