package com.comercioflex.dashboard.infrastructure.jdbc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.comercioflex.dashboard.application.DashboardMetrics;
import com.comercioflex.dashboard.application.DashboardRepository;
import com.comercioflex.dashboard.application.DashboardSettings;
import com.comercioflex.dashboard.application.LowStockVariant;

@Repository
public class JdbcDashboardRepository implements DashboardRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcDashboardRepository(
			@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public DashboardSettings findSettings() {
		List<DashboardSettings> settings = jdbcTemplate.query("""
			SELECT currency_code, timezone, low_stock_threshold
			FROM store_settings
			ORDER BY id
			LIMIT 1
			""",
			(resultSet, rowNumber) -> new DashboardSettings(
				resultSet.getString("currency_code"),
				resultSet.getString("timezone"),
				resultSet.getBigDecimal("low_stock_threshold")));
		if (settings.size() != 1) {
			throw new IllegalStateException("El comercio no tiene una configuración válida.");
		}
		return settings.getFirst();
	}

	@Override
	public DashboardMetrics findMetrics(
			Instant dayStart,
			Instant nextDayStart,
			Instant monthStart,
			Instant nextMonthStart,
			BigDecimal lowStockThreshold,
			int criticalStockLimit) {
		BigDecimal salesToday = salesBetween(dayStart, nextDayStart);
		BigDecimal salesThisMonth = salesBetween(monthStart, nextMonthStart);
		Long openOrders = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM orders
			WHERE status IN ('CONFIRMED', 'READY_FOR_PICKUP')
			""", Long.class);
		Long lowStockVariants = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM product_variants variant
			JOIN products product ON product.id = variant.product_id
			LEFT JOIN inventory_balances balance ON balance.variant_id = variant.id
			WHERE variant.status = 'ACTIVE'
				AND product.status <> 'ARCHIVED'
				AND COALESCE(balance.quantity, 0.000) <= ?
			""", Long.class, lowStockThreshold);
		List<LowStockVariant> criticalStock = jdbcTemplate.query("""
			SELECT
				BIN_TO_UUID(variant.public_id) variant_public_id,
				product.name product_name,
				variant.sku,
				variant.size_value,
				variant.color_value,
				COALESCE(balance.quantity, 0.000) quantity
			FROM product_variants variant
			JOIN products product ON product.id = variant.product_id
			LEFT JOIN inventory_balances balance ON balance.variant_id = variant.id
			WHERE variant.status = 'ACTIVE'
				AND product.status <> 'ARCHIVED'
				AND COALESCE(balance.quantity, 0.000) <= ?
			ORDER BY quantity, product.name, variant.sku, variant.id
			LIMIT ?
			""",
			(resultSet, rowNumber) -> new LowStockVariant(
				UUID.fromString(resultSet.getString("variant_public_id")),
				resultSet.getString("product_name"),
				resultSet.getString("sku"),
				resultSet.getString("size_value"),
				resultSet.getString("color_value"),
				resultSet.getBigDecimal("quantity")),
			lowStockThreshold,
			criticalStockLimit);
		return new DashboardMetrics(
			salesToday,
			salesThisMonth,
			openOrders == null ? 0 : openOrders,
			lowStockVariants == null ? 0 : lowStockVariants,
			criticalStock);
	}

	@Override
	public void updateLowStockThreshold(BigDecimal threshold) {
		int changed = jdbcTemplate.update("""
			UPDATE store_settings
			SET low_stock_threshold = ?
			""", threshold);
		if (changed != 1) {
			throw new IllegalStateException("No se pudo actualizar el umbral de stock.");
		}
	}

	private BigDecimal salesBetween(Instant start, Instant end) {
		BigDecimal amount = jdbcTemplate.queryForObject("""
			SELECT COALESCE(SUM(order_record.subtotal), 0.00)
			FROM orders order_record
			JOIN (
				SELECT order_id, MIN(created_at) confirmed_at
				FROM order_status_history
				WHERE new_status = 'CONFIRMED'
				GROUP BY order_id
			) confirmation ON confirmation.order_id = order_record.id
			WHERE order_record.status IN ('CONFIRMED', 'READY_FOR_PICKUP', 'COMPLETED')
				AND confirmation.confirmed_at >= ?
				AND confirmation.confirmed_at < ?
			""",
			BigDecimal.class,
			start,
			end);
		return amount == null ? BigDecimal.ZERO.setScale(2) : amount;
	}
}
