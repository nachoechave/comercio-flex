package com.comercioflex.inventory.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.inventory.domain.AdjustmentDirection;
import com.comercioflex.inventory.domain.InventoryActor;
import com.comercioflex.inventory.domain.InventoryReason;

@Service
public class BranchInventoryService {

	private static final BigDecimal MAX_QUANTITY = new BigDecimal("999999999999.999");

	private final JdbcTemplate jdbcTemplate;
	private final TransactionTemplate transactionTemplate;

	public BranchInventoryService(
			@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate transactionTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		this.transactionTemplate = transactionTemplate;
	}

	public List<BranchView> findBranches() {
		return transactionTemplate.execute(ignored -> jdbcTemplate.query("""
			SELECT BIN_TO_UUID(public_id) public_id, name, address, active, is_default,
				created_at, updated_at
			FROM store_branches
			ORDER BY is_default DESC, active DESC, name, id
			""", (rs, rowNum) -> new BranchView(
			UUID.fromString(rs.getString("public_id")),
			rs.getString("name"),
			rs.getString("address"),
			rs.getBoolean("active"),
			rs.getBoolean("is_default"),
			rs.getTimestamp("created_at").toInstant(),
			rs.getTimestamp("updated_at").toInstant())));
	}

	public BranchView createBranch(String rawName, String rawAddress, boolean makeDefault) {
		String name = normalizeName(rawName);
		String address = normalizeAddress(rawAddress);
		return transactionTemplate.execute(ignored -> {
			if (makeDefault) {
				jdbcTemplate.update("UPDATE store_branches SET is_default = FALSE WHERE is_default = TRUE");
			}
			UUID publicId = UUID.randomUUID();
			try {
				jdbcTemplate.update("""
					INSERT INTO store_branches (public_id, name, address, active, is_default)
					VALUES (UUID_TO_BIN(?), ?, ?, TRUE, ?)
					""", publicId.toString(), name, address, makeDefault);
			}
			catch (DuplicateKeyException exception) {
				throw new InvalidInventoryAdjustmentException("Ya existe una sucursal con ese nombre.");
			}
			return requireBranch(publicId);
		});
	}

	public BranchView updateBranch(
			UUID branchId, String rawName, String rawAddress, boolean active, boolean makeDefault) {
		String name = normalizeName(rawName);
		String address = normalizeAddress(rawAddress);
		return transactionTemplate.execute(ignored -> {
			BranchLock current = lockBranch(branchId);
			if (current.isDefault() && !active) {
				throw new InvalidInventoryAdjustmentException(
					"La sucursal principal no puede desactivarse. Elegí otra principal primero.");
			}
			if (makeDefault && !active) {
				throw new InvalidInventoryAdjustmentException(
					"La sucursal principal debe estar activa.");
			}
			if (makeDefault && !current.isDefault()) {
				jdbcTemplate.update("UPDATE store_branches SET is_default = FALSE WHERE is_default = TRUE");
			}
			try {
				jdbcTemplate.update("""
					UPDATE store_branches
					SET name = ?, address = ?, active = ?, is_default = ?
					WHERE public_id = UUID_TO_BIN(?)
					""", name, address, active, makeDefault || current.isDefault(), branchId.toString());
			}
			catch (DuplicateKeyException exception) {
				throw new InvalidInventoryAdjustmentException("Ya existe una sucursal con ese nombre.");
			}
			return requireBranch(branchId);
		});
	}

	public List<BranchStockView> findVariantStock(UUID variantId) {
		return transactionTemplate.execute(ignored -> {
			requireVariantInternalId(variantId, false);
			return jdbcTemplate.query("""
				SELECT BIN_TO_UUID(branch.public_id) branch_public_id,
					branch.name, branch.active, branch.is_default,
					COALESCE(balance.quantity, 0.000) quantity,
					COALESCE(balance.version, 0) version,
					COALESCE(balance.updated_at, branch.updated_at) updated_at
				FROM store_branches branch
				LEFT JOIN product_variants variant ON variant.public_id = UUID_TO_BIN(?)
				LEFT JOIN branch_inventory_balances balance
					ON balance.branch_id = branch.id AND balance.variant_id = variant.id
				ORDER BY branch.is_default DESC, branch.active DESC, branch.name, branch.id
				""", (rs, rowNum) -> new BranchStockView(
				UUID.fromString(rs.getString("branch_public_id")),
				rs.getString("name"),
				rs.getBoolean("active"),
				rs.getBoolean("is_default"),
				rs.getBigDecimal("quantity"),
				rs.getLong("version"),
				rs.getTimestamp("updated_at").toInstant()),
				variantId.toString());
		});
	}

	public BranchAdjustmentView adjust(
			UUID variantId,
			UUID branchId,
			UUID idempotencyKey,
			AdjustmentDirection direction,
			BigDecimal rawQuantity,
			InventoryReason reason,
			String rawNote,
			InventoryActor actor) {
		BigDecimal quantity = canonical(rawQuantity);
		if (quantity.signum() <= 0 || quantity.compareTo(MAX_QUANTITY) > 0) {
			throw new InvalidInventoryAdjustmentException("La cantidad debe ser mayor que cero.");
		}
		if (quantity.stripTrailingZeros().scale() > 0) {
			throw new InvalidInventoryAdjustmentException(
				"Durante el piloto los ajustes manuales requieren unidades enteras.");
		}
		String note = normalizeNote(rawNote);
		if (reason == InventoryReason.OTHER && note == null) {
			throw new InvalidInventoryAdjustmentException("El motivo OTHER requiere una observación.");
		}

		return transactionTemplate.execute(ignored -> {
			long variantInternalId = requireVariantInternalId(variantId, true);
			BranchLock branch = lockBranch(branchId);
			if (!branch.active()) {
				throw new InvalidInventoryAdjustmentException("No se puede ajustar una sucursal inactiva.");
			}

			var replay = jdbcTemplate.query("""
				SELECT movement.direction, movement.quantity, movement.reason, movement.note,
					movement.variant_id, movement.branch_id
				FROM inventory_movements movement
				WHERE movement.idempotency_key = UUID_TO_BIN(?)
				""", (rs, rowNum) -> new StoredBranchAdjustment(
				AdjustmentDirection.valueOf(rs.getString("direction")),
				rs.getBigDecimal("quantity"),
				InventoryReason.valueOf(rs.getString("reason")),
				rs.getString("note"),
				rs.getLong("variant_id"),
				rs.getLong("branch_id")), idempotencyKey.toString()).stream().findFirst();
			if (replay.isPresent()) {
				StoredBranchAdjustment stored = replay.get();
				if (stored.variantId() != variantInternalId || stored.branchId() != branch.internalId()
						|| stored.direction() != direction || stored.quantity().compareTo(quantity) != 0
						|| stored.reason() != reason || !java.util.Objects.equals(stored.note(), note)) {
					throw new IdempotencyConflictException();
				}
				return new BranchAdjustmentView(findBranchStock(branchId, variantInternalId), true);
			}

			jdbcTemplate.update("""
				INSERT IGNORE INTO branch_inventory_balances (branch_id, variant_id, quantity)
				VALUES (?, ?, 0.000)
				""", branch.internalId(), variantInternalId);
			BigDecimal before = jdbcTemplate.queryForObject("""
				SELECT quantity FROM branch_inventory_balances
				WHERE branch_id = ? AND variant_id = ? FOR UPDATE
				""", BigDecimal.class, branch.internalId(), variantInternalId);
			BigDecimal delta = direction == AdjustmentDirection.INCREASE ? quantity : quantity.negate();
			BigDecimal after = canonical(before.add(delta));
			if (after.signum() < 0) throw new InsufficientStockException();
			if (after.compareTo(MAX_QUANTITY) > 0) throw new InventoryCapacityExceededException();

			jdbcTemplate.update("""
				INSERT IGNORE INTO inventory_balances (variant_id, quantity) VALUES (?, 0.000)
				""", variantInternalId);
			BigDecimal totalBefore = jdbcTemplate.queryForObject("""
				SELECT quantity FROM inventory_balances WHERE variant_id = ? FOR UPDATE
				""", BigDecimal.class, variantInternalId);
			BigDecimal totalAfter = canonical(totalBefore.add(delta));
			if (totalAfter.signum() < 0) throw new InsufficientStockException();

			jdbcTemplate.update("""
				UPDATE branch_inventory_balances
				SET quantity = ?, version = version + 1
				WHERE branch_id = ? AND variant_id = ?
				""", after, branch.internalId(), variantInternalId);
			jdbcTemplate.update("""
				UPDATE inventory_balances SET quantity = ?, version = version + 1 WHERE variant_id = ?
				""", totalAfter, variantInternalId);
			Long totalVersion = jdbcTemplate.queryForObject(
				"SELECT version FROM inventory_balances WHERE variant_id = ?", Long.class, variantInternalId);
			try {
				jdbcTemplate.update("""
					INSERT INTO inventory_movements (
						public_id, variant_id, branch_id, idempotency_key, direction, quantity,
						delta_quantity, quantity_before, quantity_after, balance_version,
						reason, note, actor_public_id, actor_display_name
					) VALUES (UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, ?, ?, UUID_TO_BIN(?), ?)
					""", UUID.randomUUID().toString(), variantInternalId, branch.internalId(),
					idempotencyKey.toString(), direction.name(), quantity, delta, before, after,
					totalVersion, reason.name(), note, actor.id().toString(), actor.displayName());
			}
			catch (DuplicateKeyException exception) {
				throw new IdempotencyConflictException();
			}
			return new BranchAdjustmentView(findBranchStock(branchId, variantInternalId), false);
		});
	}

	private BranchStockView findBranchStock(UUID branchId, long variantInternalId) {
		return jdbcTemplate.query("""
			SELECT BIN_TO_UUID(branch.public_id) branch_public_id, branch.name,
				branch.active, branch.is_default, COALESCE(balance.quantity, 0.000) quantity,
				COALESCE(balance.version, 0) version,
				COALESCE(balance.updated_at, branch.updated_at) updated_at
			FROM store_branches branch
			LEFT JOIN branch_inventory_balances balance
				ON balance.branch_id = branch.id AND balance.variant_id = ?
			WHERE branch.public_id = UUID_TO_BIN(?)
			""", (rs, rowNum) -> new BranchStockView(
			UUID.fromString(rs.getString("branch_public_id")), rs.getString("name"),
			rs.getBoolean("active"), rs.getBoolean("is_default"), rs.getBigDecimal("quantity"),
			rs.getLong("version"), rs.getTimestamp("updated_at").toInstant()),
			variantInternalId, branchId.toString()).stream().findFirst()
			.orElseThrow(() -> new InvalidInventoryAdjustmentException("La sucursal no existe."));
	}

	private long requireVariantInternalId(UUID variantId, boolean lock) {
		String sql = "SELECT id FROM product_variants WHERE public_id = UUID_TO_BIN(?)" + (lock ? " FOR UPDATE" : "");
		return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), variantId.toString())
			.stream().findFirst().orElseThrow(InventoryNotFoundException::new);
	}

	private BranchLock lockBranch(UUID branchId) {
		return jdbcTemplate.query("""
			SELECT id, active, is_default FROM store_branches
			WHERE public_id = UUID_TO_BIN(?) FOR UPDATE
			""", (rs, rowNum) -> new BranchLock(
			rs.getLong("id"), rs.getBoolean("active"), rs.getBoolean("is_default")),
			branchId.toString()).stream().findFirst()
			.orElseThrow(() -> new InvalidInventoryAdjustmentException("La sucursal no existe."));
	}

	private BranchView requireBranch(UUID branchId) {
		return jdbcTemplate.query("""
			SELECT BIN_TO_UUID(public_id) public_id, name, address, active, is_default,
				created_at, updated_at FROM store_branches
			WHERE public_id = UUID_TO_BIN(?)
			""", (rs, rowNum) -> new BranchView(
			UUID.fromString(rs.getString("public_id")), rs.getString("name"), rs.getString("address"),
			rs.getBoolean("active"), rs.getBoolean("is_default"),
			rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()),
			branchId.toString()).stream().findFirst()
			.orElseThrow(() -> new InvalidInventoryAdjustmentException("La sucursal no existe."));
	}

	private String normalizeName(String value) {
		if (value == null || value.isBlank()) throw new InvalidInventoryAdjustmentException("El nombre es obligatorio.");
		String normalized = value.trim().replaceAll("\\s+", " ");
		if (normalized.length() > 120) throw new InvalidInventoryAdjustmentException("El nombre no puede superar 120 caracteres.");
		return normalized;
	}

	private String normalizeAddress(String value) {
		if (value == null || value.isBlank()) return null;
		String normalized = value.trim().replaceAll("\\s+", " ");
		if (normalized.length() > 255) throw new InvalidInventoryAdjustmentException("La dirección no puede superar 255 caracteres.");
		return normalized;
	}

	private String normalizeNote(String value) {
		if (value == null || value.isBlank()) return null;
		String normalized = value.trim().replaceAll("\\s+", " ");
		if (normalized.length() > 500) throw new InvalidInventoryAdjustmentException("La observación no puede superar 500 caracteres.");
		return normalized;
	}

	private BigDecimal canonical(BigDecimal value) {
		if (value == null) throw new InvalidInventoryAdjustmentException("La cantidad es obligatoria.");
		try {
			return value.setScale(3, RoundingMode.UNNECESSARY);
		}
		catch (ArithmeticException exception) {
			throw new InvalidInventoryAdjustmentException("La cantidad admite como máximo tres decimales.");
		}
	}

	public record BranchView(UUID id, String name, String address, boolean active,
		boolean defaultBranch, Instant createdAt, Instant updatedAt) {}
	public record BranchStockView(UUID branchId, String branchName, boolean active,
		boolean defaultBranch, BigDecimal quantity, long version, Instant updatedAt) {}
	public record BranchAdjustmentView(BranchStockView stock, boolean replay) {}
	private record BranchLock(long internalId, boolean active, boolean isDefault) {}
	private record StoredBranchAdjustment(AdjustmentDirection direction, BigDecimal quantity,
		InventoryReason reason, String note, long variantId, long branchId) {}
}
