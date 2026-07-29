package com.comercioflex.inventory.infrastructure.jdbc;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.comercioflex.catalog.domain.ProductStatus;
import com.comercioflex.inventory.application.AdjustmentCommand;
import com.comercioflex.inventory.application.InventoryPage;
import com.comercioflex.inventory.application.InventoryRepository;
import com.comercioflex.inventory.application.InventorySearch;
import com.comercioflex.inventory.application.LockedInventoryVariant;
import com.comercioflex.inventory.application.MovementPage;
import com.comercioflex.inventory.application.StoredAdjustment;
import com.comercioflex.inventory.application.IdempotencyConflictException;
import com.comercioflex.inventory.domain.AdjustmentDirection;
import com.comercioflex.inventory.domain.InventoryActor;
import com.comercioflex.inventory.domain.InventoryAvailability;
import com.comercioflex.inventory.domain.InventoryItem;
import com.comercioflex.inventory.domain.InventoryMovement;
import com.comercioflex.inventory.domain.InventoryReason;

@Repository
public class JdbcInventoryRepository implements InventoryRepository {

	private static final String ITEM_SELECT = """
		SELECT
			BIN_TO_UUID(variant.public_id) variant_public_id,
			BIN_TO_UUID(product.public_id) product_public_id,
			product.name product_name,
			product.status product_status,
			variant.sku,
			variant.size_value,
			variant.color_value,
			variant.status variant_status,
			COALESCE(balance.quantity, 0.000) quantity,
			COALESCE(balance.version, 0) balance_version,
			COALESCE(balance.updated_at, variant.created_at) inventory_updated_at
		FROM product_variants variant
		JOIN products product ON product.id = variant.product_id
		LEFT JOIN inventory_balances balance ON balance.variant_id = variant.id
		""";

	private static final String MOVEMENT_SELECT = """
		SELECT
			BIN_TO_UUID(movement.public_id) movement_public_id,
			movement.direction,
			movement.delta_quantity,
			movement.quantity_before,
			movement.quantity_after,
			movement.reason,
			movement.note,
			BIN_TO_UUID(movement.actor_public_id) actor_public_id,
			movement.actor_display_name,
			movement.created_at
		FROM inventory_movements movement
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcInventoryRepository(
			@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public InventoryPage findPage(InventorySearch search) {
		StringBuilder where = new StringBuilder(" WHERE 1=1");
		List<Object> parameters = new ArrayList<>();
		appendFilters(where, parameters, search);
		Long total = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM product_variants variant
			JOIN products product ON product.id = variant.product_id
			LEFT JOIN inventory_balances balance ON balance.variant_id = variant.id
			""" + where,
			Long.class,
			parameters.toArray());
		parameters.add(search.size());
		parameters.add(Math.multiplyExact((long) search.page(), search.size()));
		List<InventoryItem> items = jdbcTemplate.query(
			ITEM_SELECT + where
				+ " ORDER BY product.name, variant.sku, variant.id LIMIT ? OFFSET ?",
			this::mapItem,
			parameters.toArray());
		return new InventoryPage(
			items,
			search.page(),
			search.size(),
			total == null ? 0 : total);
	}

	@Override
	public Optional<InventoryItem> findItem(UUID variantId) {
		return jdbcTemplate.query(
			ITEM_SELECT + " WHERE variant.public_id = UUID_TO_BIN(?)",
			this::mapItem,
			variantId.toString())
			.stream()
			.findFirst();
	}

	@Override
	public Optional<LockedInventoryVariant> lockVariant(UUID variantId) {
		return jdbcTemplate.query("""
			SELECT id
			FROM product_variants
			WHERE public_id = UUID_TO_BIN(?)
			FOR UPDATE
			""",
			(resultSet, rowNumber) ->
				new LockedInventoryVariant(resultSet.getLong("id")),
			variantId.toString())
			.stream()
			.findFirst();
	}

	@Override
	public void ensureBalance(long variantInternalId) {
		jdbcTemplate.update("""
			INSERT IGNORE INTO inventory_balances (variant_id, quantity)
			VALUES (?, 0.000)
			""",
			variantInternalId);
	}

	@Override
	public BigDecimal findBalanceForUpdate(long variantInternalId) {
		return jdbcTemplate.queryForObject("""
			SELECT quantity
			FROM inventory_balances
			WHERE variant_id = ?
			FOR UPDATE
			""",
			BigDecimal.class,
			variantInternalId);
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
			throw new IllegalStateException("No se pudo actualizar el saldo de inventario.");
		}
		Long version = jdbcTemplate.queryForObject("""
			SELECT version
			FROM inventory_balances
			WHERE variant_id = ?
			""",
			Long.class,
			variantInternalId);
		if (version == null) {
			throw new IllegalStateException("No se pudo leer la versiÃ³n del saldo.");
		}
		return version;
	}

	@Override
	public Optional<StoredAdjustment> findAdjustment(UUID idempotencyKey) {
		return jdbcTemplate.query("""
			SELECT
				movement.variant_id,
				movement.direction,
				movement.quantity,
				movement.reason,
				movement.note,
				BIN_TO_UUID(movement.public_id) movement_public_id,
				movement.delta_quantity,
				movement.quantity_before,
				movement.quantity_after,
				BIN_TO_UUID(movement.actor_public_id) actor_public_id,
				movement.actor_display_name,
				movement.created_at
			FROM inventory_movements movement
			WHERE movement.idempotency_key = UUID_TO_BIN(?)
			""",
			(resultSet, rowNumber) -> new StoredAdjustment(
				resultSet.getLong("variant_id"),
				AdjustmentDirection.valueOf(resultSet.getString("direction")),
				resultSet.getBigDecimal("quantity"),
				InventoryReason.valueOf(resultSet.getString("reason")),
				resultSet.getString("note"),
				mapMovement(resultSet, rowNumber)),
			idempotencyKey.toString())
			.stream()
			.findFirst();
	}

	@Override
	public void insertMovement(
			UUID movementId,
			long variantInternalId,
			AdjustmentCommand command,
			BigDecimal delta,
			BigDecimal before,
			BigDecimal after,
			long balanceVersion) {
		try {
			jdbcTemplate.update("""
				INSERT INTO inventory_movements (
				public_id,
				variant_id,
				idempotency_key,
				direction,
				quantity,
				delta_quantity,
				quantity_before,
				quantity_after,
				balance_version,
				reason,
				note,
				actor_public_id,
				actor_display_name
				)
				VALUES (
				UUID_TO_BIN(?), ?, UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, ?, ?,
				UUID_TO_BIN(?), ?
				)
				""",
				movementId.toString(),
				variantInternalId,
				command.idempotencyKey().toString(),
				command.direction().name(),
				command.quantity(),
				delta,
				before,
				after,
				balanceVersion,
				command.reason().name(),
				command.note(),
				command.actor().id().toString(),
				command.actor().displayName());
		}
		catch (DuplicateKeyException exception) {
			throw new IdempotencyConflictException();
		}
	}

	@Override
	public Optional<InventoryMovement> findMovement(
			long variantInternalId,
			UUID idempotencyKey) {
		return jdbcTemplate.query(
			MOVEMENT_SELECT + """
				WHERE movement.variant_id = ?
					AND movement.idempotency_key = UUID_TO_BIN(?)
				""",
			this::mapMovement,
			variantInternalId,
			idempotencyKey.toString())
			.stream()
			.findFirst();
	}

	@Override
	public MovementPage findMovements(UUID variantId, int page, int size) {
		Long total = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM inventory_movements movement
			JOIN product_variants variant ON variant.id = movement.variant_id
			WHERE variant.public_id = UUID_TO_BIN(?)
			""",
			Long.class,
			variantId.toString());
		List<InventoryMovement> items = jdbcTemplate.query(
			MOVEMENT_SELECT + """
				JOIN product_variants variant ON variant.id = movement.variant_id
				WHERE variant.public_id = UUID_TO_BIN(?)
				ORDER BY movement.created_at DESC, movement.id DESC
				LIMIT ? OFFSET ?
				""",
			this::mapMovement,
			variantId.toString(),
			size,
			Math.multiplyExact((long) page, size));
		return new MovementPage(items, page, size, total == null ? 0 : total);
	}

	private void appendFilters(
			StringBuilder where,
			List<Object> parameters,
			InventorySearch search) {
		if (search.query() != null) {
			where.append(" AND (product.name LIKE ? OR variant.sku LIKE ?)");
			String pattern = "%" + search.query() + "%";
			parameters.add(pattern);
			parameters.add(pattern);
		}
		if (search.availability() == InventoryAvailability.IN_STOCK) {
			where.append(" AND COALESCE(balance.quantity, 0.000) > 0");
		}
		else if (search.availability() == InventoryAvailability.OUT_OF_STOCK) {
			where.append(" AND COALESCE(balance.quantity, 0.000) = 0");
		}
	}

	private InventoryItem mapItem(ResultSet resultSet, int rowNumber)
			throws SQLException {
		return new InventoryItem(
			UUID.fromString(resultSet.getString("variant_public_id")),
			UUID.fromString(resultSet.getString("product_public_id")),
			resultSet.getString("product_name"),
			ProductStatus.valueOf(resultSet.getString("product_status")),
			resultSet.getString("sku"),
			nullableOption(resultSet.getString("size_value")),
			nullableOption(resultSet.getString("color_value")),
			"ACTIVE".equals(resultSet.getString("variant_status")),
			resultSet.getBigDecimal("quantity"),
			resultSet.getLong("balance_version"),
			resultSet.getTimestamp("inventory_updated_at").toInstant());
	}

	private InventoryMovement mapMovement(ResultSet resultSet, int rowNumber)
			throws SQLException {
		return new InventoryMovement(
			UUID.fromString(resultSet.getString("movement_public_id")),
			AdjustmentDirection.valueOf(resultSet.getString("direction")),
			resultSet.getBigDecimal("delta_quantity"),
			resultSet.getBigDecimal("quantity_before"),
			resultSet.getBigDecimal("quantity_after"),
			InventoryReason.valueOf(resultSet.getString("reason")),
			resultSet.getString("note"),
			new InventoryActor(
				UUID.fromString(resultSet.getString("actor_public_id")),
				resultSet.getString("actor_display_name")),
			resultSet.getTimestamp("created_at").toInstant());
	}

	private String nullableOption(String value) {
		return value == null || value.isEmpty() ? null : value;
	}
}
