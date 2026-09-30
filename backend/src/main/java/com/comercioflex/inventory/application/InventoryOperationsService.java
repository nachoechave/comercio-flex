package com.comercioflex.inventory.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.inventory.domain.InventoryActor;

@Service
public class InventoryOperationsService {

	private static final BigDecimal ZERO = new BigDecimal("0.000");
	private static final BigDecimal MAX_QUANTITY = new BigDecimal("999999999999.999");

	private final JdbcTemplate jdbcTemplate;
	private final TransactionTemplate transactionTemplate;

	public InventoryOperationsService(
			@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate transactionTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		this.transactionTemplate = transactionTemplate;
	}

	public TransferResult transfer(
			UUID idempotencyKey,
			UUID fromBranchId,
			UUID toBranchId,
			List<TransferLine> rawItems,
			String rawNote,
			InventoryActor actor) {
		if (idempotencyKey == null) {
			throw new InvalidInventoryAdjustmentException("Idempotency-Key es obligatorio.");
		}
		if (fromBranchId == null || toBranchId == null) {
			throw new InvalidInventoryAdjustmentException("La sucursal de origen y destino son obligatorias.");
		}
		if (fromBranchId.equals(toBranchId)) {
			throw new InvalidInventoryAdjustmentException("La sucursal de origen y destino deben ser distintas.");
		}
		List<TransferLine> items = normalizeItems(rawItems);
		String note = normalizeNote(rawNote);
		byte[] fingerprint = fingerprint(fromBranchId, toBranchId, items, note);

		return transactionTemplate.execute(ignored -> {
			StoredTransfer replay = findStoredTransfer(idempotencyKey);
			if (replay != null) {
				if (!Arrays.equals(replay.fingerprint(), fingerprint)) {
					throw new IdempotencyConflictException();
				}
				return new TransferResult(findTransfer(replay.internalId()), true);
			}

			Map<UUID, Branch> branches = lockBranches(fromBranchId, toBranchId);
			Branch from = branches.get(fromBranchId);
			Branch to = branches.get(toBranchId);
			if (from == null || to == null) {
				throw new InvalidInventoryAdjustmentException("Una de las sucursales no existe.");
			}
			if (!from.active() || !to.active()) {
				throw new InvalidInventoryAdjustmentException("Las transferencias requieren sucursales activas.");
			}

			UUID transferId = UUID.randomUUID();
			try {
				jdbcTemplate.update("""
					INSERT INTO inventory_transfers (
						public_id, idempotency_key, request_fingerprint,
						from_branch_id, to_branch_id, actor_public_id,
						actor_display_name, note
					) VALUES (
						UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, UUID_TO_BIN(?), ?, ?
					)
					""",
					transferId.toString(), idempotencyKey.toString(), fingerprint,
					from.internalId(), to.internalId(), actor.id().toString(),
					actor.displayName(), note);
			}
			catch (DuplicateKeyException exception) {
				StoredTransfer stored = findStoredTransfer(idempotencyKey);
				if (stored != null && Arrays.equals(stored.fingerprint(), fingerprint)) {
					return new TransferResult(findTransfer(stored.internalId()), true);
				}
				throw new IdempotencyConflictException();
			}

			Long transferInternalId = jdbcTemplate.queryForObject(
				"SELECT id FROM inventory_transfers WHERE public_id = UUID_TO_BIN(?)",
				Long.class, transferId.toString());
			if (transferInternalId == null) {
				throw new IllegalStateException("No se pudo crear la transferencia.");
			}

			for (TransferLine item : items.stream()
					.sorted(Comparator.comparing(line -> line.variantId().toString())).toList()) {
				applyTransferItem(transferInternalId, transferId, from, to, item, actor);
			}
			return new TransferResult(findTransfer(transferInternalId), false);
		});
	}

	public List<TransferView> listTransfers(int limit) {
		int safeLimit = Math.max(1, Math.min(limit, 100));
		return transactionTemplate.execute(ignored -> jdbcTemplate.query("""
			SELECT transfer.id, BIN_TO_UUID(transfer.public_id) public_id,
				BIN_TO_UUID(source.public_id) source_public_id, source.name source_name,
				BIN_TO_UUID(destination.public_id) destination_public_id, destination.name destination_name,
				transfer.actor_display_name, transfer.note, transfer.created_at,
				COUNT(item.id) item_count, COALESCE(SUM(item.quantity), 0.000) total_units
			FROM inventory_transfers transfer
			JOIN store_branches source ON source.id = transfer.from_branch_id
			JOIN store_branches destination ON destination.id = transfer.to_branch_id
			LEFT JOIN inventory_transfer_items item ON item.transfer_id = transfer.id
			GROUP BY transfer.id, transfer.public_id, source.public_id, source.name,
				destination.public_id, destination.name, transfer.actor_display_name,
				transfer.note, transfer.created_at
			ORDER BY transfer.created_at DESC, transfer.id DESC
			LIMIT ?
			""", (rs, rowNum) -> new TransferView(
			UUID.fromString(rs.getString("public_id")),
			UUID.fromString(rs.getString("source_public_id")), rs.getString("source_name"),
			UUID.fromString(rs.getString("destination_public_id")), rs.getString("destination_name"),
			rs.getInt("item_count"), rs.getBigDecimal("total_units"),
			rs.getString("actor_display_name"), rs.getString("note"),
			rs.getTimestamp("created_at").toInstant()), safeLimit));
	}

	public List<MovementView> listMovements(UUID branchId, int limit) {
		int safeLimit = Math.max(1, Math.min(limit, 200));
		return transactionTemplate.execute(ignored -> {
			String branchFilter = branchId == null ? "" : " AND branch.public_id = UUID_TO_BIN(?)";
			String sql = """
				SELECT BIN_TO_UUID(movement.public_id) public_id,
					BIN_TO_UUID(variant.public_id) variant_public_id,
					product.name product_name, variant.sku,
					BIN_TO_UUID(branch.public_id) branch_public_id, branch.name branch_name,
					movement.direction, movement.delta_quantity, movement.reason, movement.note,
					movement.actor_display_name, movement.created_at
				FROM inventory_movements movement
				JOIN product_variants variant ON variant.id = movement.variant_id
				JOIN products product ON product.id = variant.product_id
				LEFT JOIN store_branches branch ON branch.id = movement.branch_id
				WHERE 1=1
				""" + branchFilter + """
				ORDER BY movement.created_at DESC, movement.id DESC
				LIMIT ?
				""";
			Object[] parameters = branchId == null
				? new Object[] { safeLimit }
				: new Object[] { branchId.toString(), safeLimit };
			return jdbcTemplate.query(sql, (rs, rowNum) -> new MovementView(
				UUID.fromString(rs.getString("public_id")),
				UUID.fromString(rs.getString("variant_public_id")),
				rs.getString("product_name"), rs.getString("sku"),
				rs.getString("branch_public_id") == null ? null : UUID.fromString(rs.getString("branch_public_id")),
				rs.getString("branch_name"), rs.getString("direction"),
				rs.getBigDecimal("delta_quantity"), rs.getString("reason"), rs.getString("note"),
				rs.getString("actor_display_name"), rs.getTimestamp("created_at").toInstant()), parameters);
		});
	}

	public LowStockSummary lowStockAlerts(UUID branchId, int limit) {
		int safeLimit = Math.max(1, Math.min(limit, 200));
		return transactionTemplate.execute(ignored -> {
			BigDecimal threshold = jdbcTemplate.queryForObject(
				"SELECT low_stock_threshold FROM store_settings LIMIT 1", BigDecimal.class);
			if (threshold == null) threshold = new BigDecimal("5.000");
			String branchFilter = branchId == null ? "" : " AND branch.public_id = UUID_TO_BIN(?)";
			String sql = """
				SELECT BIN_TO_UUID(branch.public_id) branch_public_id, branch.name branch_name,
					BIN_TO_UUID(variant.public_id) variant_public_id,
					product.name product_name, variant.sku, balance.quantity
				FROM branch_inventory_balances balance
				JOIN store_branches branch ON branch.id = balance.branch_id
				JOIN product_variants variant ON variant.id = balance.variant_id
				JOIN products product ON product.id = variant.product_id
				JOIN categories category ON category.id = product.category_id
				WHERE branch.active = TRUE
					AND variant.status = 'ACTIVE'
					AND product.status = 'PUBLISHED'
					AND category.status = 'ACTIVE'
					AND balance.quantity <= ?
				""" + branchFilter + """
				ORDER BY balance.quantity ASC, product.name, variant.sku, branch.name
				LIMIT ?
				""";
			Object[] parameters = branchId == null
				? new Object[] { threshold, safeLimit }
				: new Object[] { threshold, branchId.toString(), safeLimit };
			List<LowStockAlert> alerts = jdbcTemplate.query(sql, (rs, rowNum) -> new LowStockAlert(
				UUID.fromString(rs.getString("branch_public_id")), rs.getString("branch_name"),
				UUID.fromString(rs.getString("variant_public_id")), rs.getString("product_name"),
				rs.getString("sku"), rs.getBigDecimal("quantity")), parameters);
			return new LowStockSummary(threshold, alerts);
		});
	}

	private void applyTransferItem(
			long transferInternalId,
			UUID transferId,
			Branch from,
			Branch to,
			TransferLine item,
			InventoryActor actor) {
		Variant variant = jdbcTemplate.query("""
			SELECT variant.id, product.name, variant.sku
			FROM product_variants variant
			JOIN products product ON product.id = variant.product_id
			WHERE variant.public_id = UUID_TO_BIN(?)
			FOR UPDATE
			""", (rs, rowNum) -> new Variant(
			rs.getLong("id"), rs.getString("name"), rs.getString("sku")),
			item.variantId().toString()).stream().findFirst()
			.orElseThrow(InventoryNotFoundException::new);

		jdbcTemplate.update("""
			INSERT IGNORE INTO branch_inventory_balances (branch_id, variant_id, quantity)
			VALUES (?, ?, 0.000), (?, ?, 0.000)
			""", from.internalId(), variant.internalId(), to.internalId(), variant.internalId());

		BigDecimal sourceBefore = jdbcTemplate.queryForObject("""
			SELECT quantity FROM branch_inventory_balances
			WHERE branch_id = ? AND variant_id = ? FOR UPDATE
			""", BigDecimal.class, from.internalId(), variant.internalId());
		BigDecimal destinationBefore = jdbcTemplate.queryForObject("""
			SELECT quantity FROM branch_inventory_balances
			WHERE branch_id = ? AND variant_id = ? FOR UPDATE
			""", BigDecimal.class, to.internalId(), variant.internalId());
		if (sourceBefore == null || destinationBefore == null) {
			throw new IllegalStateException("No se pudo leer el stock de las sucursales.");
		}
		sourceBefore = canonical(sourceBefore);
		destinationBefore = canonical(destinationBefore);

		BigDecimal reserved = ZERO;
		if (from.defaultBranch()) {
			reserved = jdbcTemplate.query("""
				SELECT quantity FROM inventory_reservations
				WHERE variant_id = ? AND status = 'ACTIVE'
					AND expires_at > UTC_TIMESTAMP(6)
				FOR UPDATE
				""", (rs, rowNum) -> rs.getBigDecimal("quantity"), variant.internalId())
				.stream().reduce(ZERO, BigDecimal::add);
		}
		BigDecimal available = canonical(sourceBefore.subtract(reserved).max(ZERO));
		if (item.quantity().compareTo(available) > 0) {
			throw new InsufficientStockException();
		}
		BigDecimal sourceAfter = canonical(sourceBefore.subtract(item.quantity()));
		BigDecimal destinationAfter = canonical(destinationBefore.add(item.quantity()));
		if (destinationAfter.compareTo(MAX_QUANTITY) > 0) {
			throw new InventoryCapacityExceededException();
		}

		jdbcTemplate.update("""
			UPDATE branch_inventory_balances
			SET quantity = ?, version = version + 1
			WHERE branch_id = ? AND variant_id = ?
			""", sourceAfter, from.internalId(), variant.internalId());
		jdbcTemplate.update("""
			UPDATE branch_inventory_balances
			SET quantity = ?, version = version + 1
			WHERE branch_id = ? AND variant_id = ?
			""", destinationAfter, to.internalId(), variant.internalId());

		jdbcTemplate.update("""
			INSERT IGNORE INTO inventory_balances (variant_id, quantity) VALUES (?, 0.000)
			""", variant.internalId());
		jdbcTemplate.update("""
			UPDATE inventory_balances SET version = version + 2 WHERE variant_id = ?
			""", variant.internalId());
		Long endingVersion = jdbcTemplate.queryForObject(
			"SELECT version FROM inventory_balances WHERE variant_id = ?",
			Long.class, variant.internalId());
		if (endingVersion == null || endingVersion < 2) {
			throw new IllegalStateException("No se pudo versionar la transferencia de stock.");
		}

		jdbcTemplate.update("""
			INSERT INTO inventory_transfer_items (transfer_id, variant_id, quantity)
			VALUES (?, ?, ?)
			""", transferInternalId, variant.internalId(), item.quantity());

		insertTransferMovement(
			transferInternalId, variant.internalId(), from.internalId(), item.quantity(),
			sourceBefore, sourceAfter, endingVersion - 1, "TRANSFER_OUT",
			"Transferencia " + transferId + " hacia " + to.name(), actor);
		insertTransferMovement(
			transferInternalId, variant.internalId(), to.internalId(), item.quantity(),
			destinationBefore, destinationAfter, endingVersion, "TRANSFER_IN",
			"Transferencia " + transferId + " desde " + from.name(), actor);
	}

	private void insertTransferMovement(
			long transferInternalId,
			long variantInternalId,
			long branchInternalId,
			BigDecimal quantity,
			BigDecimal before,
			BigDecimal after,
			long balanceVersion,
			String reason,
			String note,
			InventoryActor actor) {
		boolean increase = "TRANSFER_IN".equals(reason);
		jdbcTemplate.update("""
			INSERT INTO inventory_movements (
				public_id, variant_id, order_id, pos_sale_id, transfer_id, branch_id,
				idempotency_key, direction, quantity, delta_quantity,
				quantity_before, quantity_after, balance_version,
				reason, note, actor_public_id, actor_display_name
			) VALUES (
				UUID_TO_BIN(?), ?, NULL, NULL, ?, ?, UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, ?, ?, UUID_TO_BIN(?), ?
			)
			""",
			UUID.randomUUID().toString(), variantInternalId, transferInternalId, branchInternalId,
			UUID.randomUUID().toString(), increase ? "INCREASE" : "DECREASE", quantity,
			increase ? quantity : quantity.negate(), before, after, balanceVersion,
			reason, note, actor.id().toString(), actor.displayName());
	}

	private Map<UUID, Branch> lockBranches(UUID first, UUID second) {
		Map<UUID, Branch> result = new LinkedHashMap<>();
		jdbcTemplate.query("""
			SELECT id, BIN_TO_UUID(public_id) public_id, name, active, is_default
			FROM store_branches
			WHERE public_id IN (UUID_TO_BIN(?), UUID_TO_BIN(?))
			ORDER BY id FOR UPDATE
			""", rs -> {
			UUID publicId = UUID.fromString(rs.getString("public_id"));
			result.put(publicId, new Branch(
				rs.getLong("id"), publicId, rs.getString("name"),
				rs.getBoolean("active"), rs.getBoolean("is_default")));
		}, first.toString(), second.toString());
		return result;
	}

	private StoredTransfer findStoredTransfer(UUID idempotencyKey) {
		return jdbcTemplate.query("""
			SELECT id, request_fingerprint FROM inventory_transfers
			WHERE idempotency_key = UUID_TO_BIN(?)
			""", (rs, rowNum) -> new StoredTransfer(
			rs.getLong("id"), rs.getBytes("request_fingerprint")),
			idempotencyKey.toString()).stream().findFirst().orElse(null);
	}

	private TransferView findTransfer(long internalId) {
		return jdbcTemplate.query("""
			SELECT transfer.id, BIN_TO_UUID(transfer.public_id) public_id,
				BIN_TO_UUID(source.public_id) source_public_id, source.name source_name,
				BIN_TO_UUID(destination.public_id) destination_public_id, destination.name destination_name,
				transfer.actor_display_name, transfer.note, transfer.created_at,
				COUNT(item.id) item_count, COALESCE(SUM(item.quantity), 0.000) total_units
			FROM inventory_transfers transfer
			JOIN store_branches source ON source.id = transfer.from_branch_id
			JOIN store_branches destination ON destination.id = transfer.to_branch_id
			LEFT JOIN inventory_transfer_items item ON item.transfer_id = transfer.id
			WHERE transfer.id = ?
			GROUP BY transfer.id, transfer.public_id, source.public_id, source.name,
				destination.public_id, destination.name, transfer.actor_display_name,
				transfer.note, transfer.created_at
			""", (rs, rowNum) -> new TransferView(
			UUID.fromString(rs.getString("public_id")),
			UUID.fromString(rs.getString("source_public_id")), rs.getString("source_name"),
			UUID.fromString(rs.getString("destination_public_id")), rs.getString("destination_name"),
			rs.getInt("item_count"), rs.getBigDecimal("total_units"),
			rs.getString("actor_display_name"), rs.getString("note"),
			rs.getTimestamp("created_at").toInstant()), internalId).stream().findFirst()
			.orElseThrow(() -> new IllegalStateException("No se pudo leer la transferencia."));
	}

	private List<TransferLine> normalizeItems(List<TransferLine> rawItems) {
		if (rawItems == null || rawItems.isEmpty()) {
			throw new InvalidInventoryAdjustmentException("Agregá al menos un producto a la transferencia.");
		}
		if (rawItems.size() > 100) {
			throw new InvalidInventoryAdjustmentException("Una transferencia admite hasta 100 variantes.");
		}
		Map<UUID, BigDecimal> grouped = new LinkedHashMap<>();
		for (TransferLine line : rawItems) {
			if (line == null || line.variantId() == null || line.quantity() == null) {
				throw new InvalidInventoryAdjustmentException("Cada ítem debe indicar variante y cantidad.");
			}
			BigDecimal quantity = canonical(line.quantity());
			if (quantity.signum() <= 0 || quantity.compareTo(MAX_QUANTITY) > 0) {
				throw new InvalidInventoryAdjustmentException("Las cantidades deben ser mayores que cero.");
			}
			grouped.merge(line.variantId(), quantity, BigDecimal::add);
		}
		List<TransferLine> normalized = new ArrayList<>();
		grouped.forEach((variantId, quantity) -> normalized.add(new TransferLine(variantId, canonical(quantity))));
		return normalized;
	}

	private String normalizeNote(String raw) {
		if (raw == null || raw.isBlank()) return null;
		String note = raw.trim().replaceAll("\\s+", " ");
		if (note.length() > 500) {
			throw new InvalidInventoryAdjustmentException("La observación no puede superar 500 caracteres.");
		}
		return note;
	}

	private BigDecimal canonical(BigDecimal value) {
		if (value == null) {
			throw new InvalidInventoryAdjustmentException("La cantidad es obligatoria.");
		}
		try {
			return value.setScale(3, RoundingMode.UNNECESSARY);
		}
		catch (ArithmeticException exception) {
			throw new InvalidInventoryAdjustmentException("La cantidad admite como máximo tres decimales.");
		}
	}

	private byte[] fingerprint(UUID from, UUID to, List<TransferLine> items, String note) {
		StringBuilder value = new StringBuilder(from.toString()).append('|').append(to).append('|')
			.append(note == null ? "" : note);
		items.stream().sorted(Comparator.comparing(line -> line.variantId().toString()))
			.forEach(line -> value.append('|').append(line.variantId()).append(':').append(line.quantity().toPlainString()));
		try {
			return MessageDigest.getInstance("SHA-256")
				.digest(value.toString().getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 no está disponible.", exception);
		}
	}

	public record TransferLine(UUID variantId, BigDecimal quantity) {}
	public record TransferResult(TransferView transfer, boolean replay) {}
	public record TransferView(
		UUID id,
		UUID fromBranchId,
		String fromBranchName,
		UUID toBranchId,
		String toBranchName,
		int itemCount,
		BigDecimal totalUnits,
		String actorDisplayName,
		String note,
		Instant createdAt) {}
	public record MovementView(
		UUID id,
		UUID variantId,
		String productName,
		String sku,
		UUID branchId,
		String branchName,
		String direction,
		BigDecimal delta,
		String reason,
		String note,
		String actorDisplayName,
		Instant createdAt) {}
	public record LowStockSummary(BigDecimal threshold, List<LowStockAlert> alerts) {}
	public record LowStockAlert(
		UUID branchId,
		String branchName,
		UUID variantId,
		String productName,
		String sku,
		BigDecimal quantity) {}

	private record StoredTransfer(long internalId, byte[] fingerprint) {}
	private record Branch(long internalId, UUID publicId, String name, boolean active, boolean defaultBranch) {}
	private record Variant(long internalId, String productName, String sku) {}
}
