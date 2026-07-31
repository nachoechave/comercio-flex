package com.comercioflex.order.infrastructure.jdbc;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.comercioflex.order.application.AdminOrderDetail;
import com.comercioflex.order.application.AdminOrderPage;
import com.comercioflex.order.application.AdminOrderRepository;
import com.comercioflex.order.application.AdminOrderSearch;
import com.comercioflex.order.application.AdminOrderSummary;
import com.comercioflex.order.application.InvalidOrderTransitionException;
import com.comercioflex.order.application.LockedAdminOrder;
import com.comercioflex.order.application.OrderHistoryEntry;
import com.comercioflex.order.application.OrderStockLine;
import com.comercioflex.order.application.StoredOrderTransition;
import com.comercioflex.order.domain.FulfillmentType;
import com.comercioflex.order.domain.GuestOrderItem;
import com.comercioflex.order.domain.OrderStatus;

@Repository
public class JdbcAdminOrderRepository implements AdminOrderRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcAdminOrderRepository(
			@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public AdminOrderPage findPage(AdminOrderSearch search) {
		StringBuilder where = new StringBuilder(" WHERE 1=1");
		List<Object> parameters = new ArrayList<>();
		if (search.status() != null) {
			where.append(" AND status = ?");
			parameters.add(search.status().name());
		}
		if (search.query() != null) {
			where.append(" AND CAST(id AS CHAR) LIKE ?");
			parameters.add("%" + search.query().replaceAll("(?i)^ORD-0*", "") + "%");
		}
		Long total = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM orders" + where,
			Long.class,
			parameters.toArray());
		parameters.add(search.size());
		parameters.add(Math.multiplyExact((long) search.page(), search.size()));
		List<AdminOrderSummary> items = jdbcTemplate.query("""
			SELECT
				BIN_TO_UUID(public_id) public_id,
				id,
				status,
				fulfillment_type,
				customer_name,
				customer_phone,
				currency_code,
				subtotal,
				created_at
			FROM orders
			""" + where + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
			this::mapSummary,
			parameters.toArray());
		return new AdminOrderPage(
			items,
			search.page(),
			search.size(),
			total == null ? 0 : total);
	}

	@Override
	public void expirePendingOrders() {
		List<Long> orderIds = jdbcTemplate.queryForList("""
			SELECT id
			FROM orders
			WHERE status = 'PENDING_CONFIRMATION'
				AND reservation_expires_at <= UTC_TIMESTAMP(6)
			""", Long.class);
		orderIds.forEach(this::expireOrder);
	}

	@Override
	public Optional<AdminOrderDetail> findDetail(UUID orderId) {
		return jdbcTemplate.query("""
			SELECT
				BIN_TO_UUID(public_id) public_id,
				id,
				status,
				fulfillment_type,
				customer_name,
				customer_phone,
				customer_email,
				customer_notes,
				currency_code,
				subtotal,
				reservation_expires_at,
				created_at,
				version
			FROM orders
			WHERE public_id = UUID_TO_BIN(?)
			""",
			(resultSet, rowNumber) -> mapDetail(resultSet),
			orderId.toString())
			.stream()
			.findFirst();
	}

	@Override
	public Optional<LockedAdminOrder> lockOrder(UUID orderId) {
		return jdbcTemplate.query("""
			SELECT id, BIN_TO_UUID(public_id) public_id, status,
				reservation_expires_at, version
			FROM orders
			WHERE public_id = UUID_TO_BIN(?)
			FOR UPDATE
			""",
			(resultSet, rowNumber) -> new LockedAdminOrder(
				resultSet.getLong("id"),
				UUID.fromString(resultSet.getString("public_id")),
				OrderStatus.valueOf(resultSet.getString("status")),
				resultSet.getTimestamp("reservation_expires_at").toInstant(),
				resultSet.getLong("version")),
			orderId.toString())
			.stream()
			.findFirst();
	}

	@Override
	public Optional<StoredOrderTransition> findTransition(UUID idempotencyKey) {
		return jdbcTemplate.query("""
			SELECT BIN_TO_UUID(order_record.public_id) order_public_id,
				history.new_status, history.note
			FROM order_status_history history
			JOIN orders order_record ON order_record.id = history.order_id
			WHERE history.idempotency_key = UUID_TO_BIN(?)
			""",
			(resultSet, rowNumber) -> new StoredOrderTransition(
				UUID.fromString(resultSet.getString("order_public_id")),
				OrderStatus.valueOf(resultSet.getString("new_status")),
				resultSet.getString("note")),
			idempotencyKey.toString())
			.stream()
			.findFirst();
	}

	@Override
	public List<OrderStockLine> findStockLinesForUpdate(long orderInternalId) {
		return jdbcTemplate.query("""
			SELECT item.variant_id, BIN_TO_UUID(item.variant_public_id) variant_public_id,
				item.quantity
			FROM order_items item
			JOIN product_variants variant ON variant.id = item.variant_id
			WHERE item.order_id = ?
			ORDER BY variant.public_id
			FOR UPDATE
			""",
			(resultSet, rowNumber) -> new OrderStockLine(
				resultSet.getLong("variant_id"),
				UUID.fromString(resultSet.getString("variant_public_id")),
				resultSet.getBigDecimal("quantity")),
			orderInternalId);
	}

	@Override
	public BigDecimal findBalanceForUpdate(long variantInternalId) {
		BigDecimal quantity = jdbcTemplate.queryForObject("""
			SELECT quantity FROM inventory_balances
			WHERE variant_id = ?
			FOR UPDATE
			""",
			BigDecimal.class,
			variantInternalId);
		if (quantity == null) {
			throw new InvalidOrderTransitionException(
				"No existe un saldo físico para uno de los productos.");
		}
		return quantity;
	}

	@Override
	public long updateBalance(long variantInternalId, BigDecimal quantity) {
		int changed = jdbcTemplate.update("""
			UPDATE inventory_balances
			SET quantity = ?, version = version + 1
			WHERE variant_id = ?
			""",
			quantity,
			variantInternalId);
		if (changed != 1) {
			throw new InvalidOrderTransitionException(
				"No se pudo actualizar el stock del pedido.");
		}
		Long version = jdbcTemplate.queryForObject("""
			SELECT version FROM inventory_balances WHERE variant_id = ?
			""",
			Long.class,
			variantInternalId);
		return version == null ? 0 : version;
	}

	@Override
	public void insertInventoryMovement(
			UUID movementId,
			long orderInternalId,
			OrderStockLine line,
			BigDecimal before,
			BigDecimal after,
			long balanceVersion,
			boolean restoring,
			UUID movementIdempotencyKey,
			UUID actorId,
			String actorName) {
		jdbcTemplate.update("""
			INSERT INTO inventory_movements (
				public_id, variant_id, order_id, idempotency_key, direction,
				quantity, delta_quantity, quantity_before, quantity_after,
				balance_version, reason, note, actor_public_id, actor_display_name
			)
			VALUES (
				UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, ?, ?,
				UUID_TO_BIN(?), ?
			)
			""",
			movementId.toString(),
			line.variantInternalId(),
			orderInternalId,
			movementIdempotencyKey.toString(),
			restoring ? "INCREASE" : "DECREASE",
			line.quantity(),
			restoring ? line.quantity() : line.quantity().negate(),
			before,
			after,
			balanceVersion,
			restoring ? "ORDER_CANCELLED" : "ORDER_CONFIRMED",
			restoring ? "Reposición por cancelación de pedido" : "Consumo por pedido confirmado",
			actorId == null ? null : actorId.toString(),
			actorName);
	}

	@Override
	public int updateReservations(
			long orderInternalId,
			String fromStatus,
			String toStatus) {
		return jdbcTemplate.update("""
			UPDATE inventory_reservations
			SET status = ?
			WHERE order_id = ? AND status = ?
			""",
			toStatus,
			orderInternalId,
			fromStatus);
	}

	@Override
	public void updateOrderStatus(
			long orderInternalId,
			long version,
			OrderStatus targetStatus) {
		int changed = jdbcTemplate.update("""
			UPDATE orders
			SET status = ?, version = version + 1
			WHERE id = ? AND version = ?
			""",
			targetStatus.name(),
			orderInternalId,
			version);
		if (changed != 1) {
			throw new InvalidOrderTransitionException(
				"El pedido cambió mientras intentabas actualizarlo.");
		}
	}

	@Override
	public void insertHistory(
			long orderInternalId,
			UUID idempotencyKey,
			OrderStatus previousStatus,
			OrderStatus newStatus,
			String note,
			UUID actorId,
			String actorName) {
		jdbcTemplate.update("""
			INSERT INTO order_status_history (
				public_id, order_id, idempotency_key, previous_status, new_status,
				note, actor_public_id, actor_display_name
			)
			VALUES (
				UUID_TO_BIN(?), ?, UUID_TO_BIN(?), ?, ?, ?, UUID_TO_BIN(?), ?
			)
			""",
			UUID.randomUUID().toString(),
			orderInternalId,
			idempotencyKey.toString(),
			previousStatus.name(),
			newStatus.name(),
			note,
			actorId == null ? null : actorId.toString(),
			actorName);
	}

	@Override
	public void expireOrder(long orderInternalId) {
		int changed = jdbcTemplate.update("""
			UPDATE orders
			SET status = 'EXPIRED', version = version + 1
			WHERE id = ? AND status = 'PENDING_CONFIRMATION'
			""",
			orderInternalId);
		if (changed == 0) return;
		updateReservations(orderInternalId, "ACTIVE", "EXPIRED");
		jdbcTemplate.update("""
			INSERT INTO order_status_history (
				public_id, order_id, previous_status, new_status, actor_display_name
			)
			VALUES (UUID_TO_BIN(?), ?, 'PENDING_CONFIRMATION', 'EXPIRED', 'Sistema')
			""",
			UUID.randomUUID().toString(),
			orderInternalId);
	}

	private AdminOrderSummary mapSummary(ResultSet resultSet, int rowNumber)
			throws SQLException {
		return new AdminOrderSummary(
			UUID.fromString(resultSet.getString("public_id")),
			resultSet.getLong("id"),
			OrderStatus.valueOf(resultSet.getString("status")),
			FulfillmentType.valueOf(resultSet.getString("fulfillment_type")),
			resultSet.getString("customer_name"),
			resultSet.getString("customer_phone"),
			resultSet.getString("currency_code"),
			resultSet.getBigDecimal("subtotal"),
			resultSet.getTimestamp("created_at").toInstant());
	}

	private AdminOrderDetail mapDetail(ResultSet resultSet) throws SQLException {
		long internalId = resultSet.getLong("id");
		return new AdminOrderDetail(
			UUID.fromString(resultSet.getString("public_id")),
			internalId,
			OrderStatus.valueOf(resultSet.getString("status")),
			FulfillmentType.valueOf(resultSet.getString("fulfillment_type")),
			resultSet.getString("customer_name"),
			resultSet.getString("customer_phone"),
			resultSet.getString("customer_email"),
			resultSet.getString("customer_notes"),
			resultSet.getString("currency_code"),
			resultSet.getBigDecimal("subtotal"),
			resultSet.getTimestamp("reservation_expires_at").toInstant(),
			resultSet.getTimestamp("created_at").toInstant(),
			resultSet.getLong("version"),
			findItems(internalId),
			findHistory(internalId));
	}

	private List<GuestOrderItem> findItems(long orderInternalId) {
		return jdbcTemplate.query("""
			SELECT BIN_TO_UUID(product_public_id) product_public_id,
				BIN_TO_UUID(variant_public_id) variant_public_id, product_name,
				size_snapshot, color_snapshot, unit_code, unit_price, quantity,
				line_total
			FROM order_items
			WHERE order_id = ?
			ORDER BY id
			""",
			(resultSet, rowNumber) -> new GuestOrderItem(
				UUID.fromString(resultSet.getString("product_public_id")),
				UUID.fromString(resultSet.getString("variant_public_id")),
				resultSet.getString("product_name"),
				nullable(resultSet.getString("size_snapshot")),
				nullable(resultSet.getString("color_snapshot")),
				resultSet.getString("unit_code"),
				resultSet.getBigDecimal("unit_price"),
				resultSet.getBigDecimal("quantity"),
				resultSet.getBigDecimal("line_total")),
			orderInternalId);
	}

	private List<OrderHistoryEntry> findHistory(long orderInternalId) {
		return jdbcTemplate.query("""
			SELECT BIN_TO_UUID(public_id) public_id, previous_status, new_status,
				note, BIN_TO_UUID(actor_public_id) actor_public_id,
				actor_display_name, created_at
			FROM order_status_history
			WHERE order_id = ?
			ORDER BY created_at, id
			""",
			(resultSet, rowNumber) -> new OrderHistoryEntry(
				UUID.fromString(resultSet.getString("public_id")),
				enumOrNull(resultSet.getString("previous_status")),
				OrderStatus.valueOf(resultSet.getString("new_status")),
				resultSet.getString("note"),
				uuidOrNull(resultSet.getString("actor_public_id")),
				resultSet.getString("actor_display_name"),
				resultSet.getTimestamp("created_at").toInstant()),
			orderInternalId);
	}

	private OrderStatus enumOrNull(String value) {
		return value == null ? null : OrderStatus.valueOf(value);
	}

	private UUID uuidOrNull(String value) {
		return value == null ? null : UUID.fromString(value);
	}

	private String nullable(String value) {
		return value == null || value.isEmpty() ? null : value;
	}
}
