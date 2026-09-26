package com.comercioflex.analytics.infrastructure.jdbc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.comercioflex.analytics.application.AnalyticsRepository;

@Repository
public class JdbcAnalyticsRepository implements AnalyticsRepository {

    private final JdbcTemplate jdbc;

    public JdbcAnalyticsRepository(@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(AnalyticsEvent event) {
        if (event.productId() == null) {
            jdbc.update("""
                    INSERT INTO storefront_analytics_events
                        (event_type, visitor_id, session_id, path, source, medium, product_public_id, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, NULL, UTC_TIMESTAMP(6))
                    """,
                    event.type().name(),
                    event.visitorId().toString(),
                    event.sessionId().toString(),
                    event.path(),
                    event.source(),
                    event.medium());
            return;
        }

        jdbc.update("""
                INSERT INTO storefront_analytics_events
                    (event_type, visitor_id, session_id, path, source, medium, product_public_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, UUID_TO_BIN(?), UTC_TIMESTAMP(6))
                """,
                event.type().name(),
                event.visitorId().toString(),
                event.sessionId().toString(),
                event.path(),
                event.source(),
                event.medium(),
                event.productId().toString());
    }

    @Override
    public AnalyticsMetrics findMetrics(Instant start, Instant end) {
        EventCounts events = jdbc.queryForObject("""
                SELECT
                    COUNT(DISTINCT CASE WHEN event_type = 'PAGE_VIEW' THEN session_id END) visits,
                    COUNT(DISTINCT CASE WHEN event_type = 'PAGE_VIEW' THEN visitor_id END) visitors,
                    SUM(CASE WHEN event_type = 'PAGE_VIEW' THEN 1 ELSE 0 END) page_views,
                    SUM(CASE WHEN event_type = 'PRODUCT_VIEW' THEN 1 ELSE 0 END) product_views,
                    SUM(CASE WHEN event_type = 'ADD_TO_CART' THEN 1 ELSE 0 END) add_to_carts,
                    SUM(CASE WHEN event_type = 'BEGIN_CHECKOUT' THEN 1 ELSE 0 END) checkouts
                FROM storefront_analytics_events
                WHERE created_at >= ? AND created_at < ?
                """,
                (rs, rowNum) -> new EventCounts(
                        rs.getLong("visits"),
                        rs.getLong("visitors"),
                        rs.getLong("page_views"),
                        rs.getLong("product_views"),
                        rs.getLong("add_to_carts"),
                        rs.getLong("checkouts")),
                start,
                end);

        Long purchases = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM (
                    SELECT order_id, MIN(created_at) confirmed_at
                    FROM order_status_history
                    WHERE new_status = 'CONFIRMED'
                    GROUP BY order_id
                ) confirmations
                WHERE confirmed_at >= ? AND confirmed_at < ?
                """,
                Long.class,
                start,
                end);

        EventCounts safe = events == null ? new EventCounts(0, 0, 0, 0, 0, 0) : events;
        return new AnalyticsMetrics(
                safe.visits(),
                safe.visitors(),
                safe.pageViews(),
                safe.productViews(),
                safe.addToCarts(),
                safe.checkouts(),
                purchases == null ? 0 : purchases);
    }

    @Override
    public List<AnalyticsProduct> findTopProducts(Instant start, Instant end, int limit) {
        return jdbc.query("""
                SELECT
                    BIN_TO_UUID(product.public_id) product_public_id,
                    product.name product_name,
                    SUM(CASE WHEN event.event_type = 'PRODUCT_VIEW' THEN 1 ELSE 0 END) views,
                    SUM(CASE WHEN event.event_type = 'ADD_TO_CART' THEN 1 ELSE 0 END) add_to_carts
                FROM storefront_analytics_events event
                JOIN products product ON product.public_id = event.product_public_id
                WHERE event.created_at >= ?
                  AND event.created_at < ?
                  AND event.event_type IN ('PRODUCT_VIEW', 'ADD_TO_CART')
                GROUP BY product.id, product.public_id, product.name
                ORDER BY views DESC, add_to_carts DESC, product.name
                LIMIT ?
                """,
                (rs, rowNum) -> new AnalyticsProduct(
                        UUID.fromString(rs.getString("product_public_id")),
                        rs.getString("product_name"),
                        rs.getLong("views"),
                        rs.getLong("add_to_carts")),
                start,
                end,
                limit);
    }

    @Override
    public List<AnalyticsSource> findTopSources(Instant start, Instant end, int limit) {
        return jdbc.query("""
                SELECT source, COUNT(DISTINCT session_id) visits
                FROM storefront_analytics_events
                WHERE event_type = 'PAGE_VIEW'
                  AND created_at >= ?
                  AND created_at < ?
                GROUP BY source
                ORDER BY visits DESC, source
                LIMIT ?
                """,
                (rs, rowNum) -> new AnalyticsSource(
                        rs.getString("source"),
                        rs.getLong("visits")),
                start,
                end,
                limit);
    }

    private record EventCounts(
            long visits,
            long visitors,
            long pageViews,
            long productViews,
            long addToCarts,
            long checkouts) {
    }
}
