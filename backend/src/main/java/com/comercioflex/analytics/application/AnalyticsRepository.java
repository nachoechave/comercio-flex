package com.comercioflex.analytics.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AnalyticsRepository {

    void record(AnalyticsEvent event);

    AnalyticsMetrics findMetrics(Instant start, Instant end);

    List<AnalyticsProduct> findTopProducts(Instant start, Instant end, int limit);

    List<AnalyticsSource> findTopSources(Instant start, Instant end, int limit);

    enum EventType {
        PAGE_VIEW,
        PRODUCT_VIEW,
        ADD_TO_CART,
        BEGIN_CHECKOUT
    }

    record AnalyticsEvent(
            EventType type,
            UUID visitorId,
            UUID sessionId,
            String path,
            String source,
            String medium,
            UUID productId) {
    }

    record AnalyticsMetrics(
            long visits,
            long visitors,
            long pageViews,
            long productViews,
            long addToCarts,
            long checkouts,
            long purchases) {
    }

    record AnalyticsProduct(
            UUID productId,
            String productName,
            long views,
            long addToCarts) {
    }

    record AnalyticsSource(String source, long visits) {
    }
}
