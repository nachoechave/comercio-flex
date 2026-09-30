package com.comercioflex.pos.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.inventory.domain.InventoryActor;

@Service
public class PosSaleService {

	private static final BigDecimal MAX_QUANTITY = new BigDecimal("999999999999.999");

	private final JdbcTemplate jdbcTemplate;
	private final TransactionTemplate transactionTemplate;

	public PosSaleService(
			@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate transactionTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		this.transactionTemplate = transactionTemplate;
	}

	public List<PosCatalogItem> searchCatalog(UUID branchId, String rawQuery) {
		String query = normalizeQuery(rawQuery);
		return transactionTemplate.execute(ignored -> {
			Branch branch = requireActiveBranch(branchId, false);
			String searchClause = query == null ? "" : """
				AND (LOWER(product.name) LIKE ? OR LOWER(variant.sku) LIKE ?)
				""";
			String sql = """
				SELECT BIN_TO_UUID(variant.public_id) variant_public_id,
					product.name product_name,
					variant.sku,
					variant.size_value,
					variant.color_value,
					variant.price,
					COALESCE(branch_balance.quantity, 0.000) physical_quantity,
					CASE WHEN branch.is_default = TRUE THEN COALESCE((
						SELECT SUM(reservation.quantity)
						FROM inventory_reservations reservation
						WHERE reservation.variant_id = variant.id
							AND reservation.status = 'ACTIVE'
							AND reservation.expires_at > UTC_TIMESTAMP(6)
					), 0.000) ELSE 0.000 END reserved_quantity
				FROM product_variants variant
				JOIN products product ON product.id = variant.product_id
				JOIN categories category ON category.id = product.category_id
				JOIN store_branches branch ON branch.id = ?
				LEFT JOIN branch_inventory_balances branch_balance
					ON branch_balance.branch_id = branch.id AND branch_balance.variant_id = variant.id
				WHERE product.status = 'PUBLISHED'
					AND category.status = 'ACTIVE'
					AND variant.status = 'ACTIVE'
				""" + searchClause + """
				ORDER BY product.name, variant.sku, variant.id
				LIMIT 30
				""";
			Object[] parameters = query == null
				? new Object[] { branch.internalId() }
				: new Object[] { branch.internalId(), "%" + query + "%", "%" + query + "%" };
			return jdbcTemplate.query(sql, (rs, rowNum) -> {
				BigDecimal physical = canonical(rs.getBigDecimal("physical_quantity"));
				BigDecimal reserved = canonical(rs.getBigDecimal("reserved_quantity"));
				BigDecimal available = physical.subtract(reserved).max(BigDecimal.ZERO.setScale(3));
				return new PosCatalogItem(
					UUID.fromString(rs.getString("variant_public_id")),
					rs.getString("product_name"),
					rs.getString("sku"),
					nullableOption(rs.getString("size_value")),
					nullableOption(rs.getString("color_value")),
					rs.getBigDecimal("price").setScale(2, RoundingMode.UNNECESSARY),
					available);
			}, parameters);
		});
	}

	public List<PosSaleView> listSales() {
		return transactionTemplate.execute(ignored -> jdbcTemplate.query("""
			SELECT sale.id, BIN_TO_UUID(sale.public_id) public_id,
				BIN_TO_UUID(branch.public_id) branch_public_id, branch.name branch_name,
				sale.seller_display_name, sale.payment_method, sale.currency_code,
				sale.subtotal, sale.created_at
			FROM pos_sales sale
			JOIN store_branches branch ON branch.id = sale.branch_id
			ORDER BY sale.created_at DESC, sale.id DESC
			LIMIT 50
			""", (rs, rowNum) -> mapSale(
			rs.getLong("id"),
			UUID.fromString(rs.getString("public_id")),
			UUID.fromString(rs.getString("branch_public_id")),
			rs.getString("branch_name"),
			rs.getString("seller_display_name"),
			PosPaymentMethod.valueOf(rs.getString("payment_method")),
			rs.getString("currency_code"),
			rs.getBigDecimal("subtotal"),
			rs.getTimestamp("created_at").toInstant())));
	}

	public PosSaleResult createSale(
			UUID idempotencyKey,
			UUID branchId,
			PosPaymentMethod paymentMethod,
			List<PosSaleLine> rawItems,
			InventoryActor actor) {
		if (idempotencyKey == null) throw invalid("Idempotency-Key es obligatorio.");
		if (branchId == null) throw invalid("La sucursal es obligatoria.");
		if (paymentMethod == null) throw invalid("El medio de pago es obligatorio.");
		List<PosSaleLine> items = normalizeItems(rawItems);
		byte[] fingerprint = fingerprint(branchId, paymentMethod, items);

		return transactionTemplate.execute(ignored -> {
			StoredSale replay = findByIdempotencyKey(idempotencyKey);
			if (replay != null) {
				if (!Arrays.equals(replay.fingerprint(), fingerprint)) {
					throw conflict("La clave de idempotencia ya fue usada con otra venta.");
				}
				return new PosSaleResult(findSaleByInternalId(replay.internalId()), true);
			}

			Branch branch = requireActiveBranch(branchId, true);
			List<PreparedItem> prepared = items.stream()
				.sorted(Comparator.comparing(line -> line.variantId().toString()))
				.map(line -> prepareItem(branch, line))
				.toList();

			BigDecimal subtotal = prepared.stream()
				.map(PreparedItem::lineTotal)
				.reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
			if (subtotal.signum() <= 0) throw invalid("La venta debe tener un total mayor a cero.");

			String currencyCode = jdbcTemplate.queryForObject(
				"SELECT currency_code FROM store_settings LIMIT 1", String.class);
			if (currencyCode == null || currencyCode.isBlank()) {
				throw new IllegalStateException("No se pudo obtener la moneda del comercio.");
			}

			UUID saleId = UUID.randomUUID();
			try {
				jdbcTemplate.update("""
					INSERT INTO pos_sales (
						public_id, idempotency_key, request_fingerprint, branch_id,
						seller_public_id, seller_display_name, payment_method,
						currency_code, subtotal
					) VALUES (
						UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), ?, ?, ?, ?
					)
					""",
					saleId.toString(), idempotencyKey.toString(), fingerprint, branch.internalId(),
					actor.id().toString(), actor.displayName(), paymentMethod.name(),
					currencyCode, subtotal);
			}
			catch (DuplicateKeyException exception) {
				StoredSale stored = findByIdempotencyKey(idempotencyKey);
				if (stored != null && Arrays.equals(stored.fingerprint(), fingerprint)) {
					return new PosSaleResult(findSaleByInternalId(stored.internalId()), true);
				}
				throw conflict("La clave de idempotencia ya fue usada con otra venta.");
			}

			Long saleInternalId = jdbcTemplate.queryForObject(
				"SELECT id FROM pos_sales WHERE public_id = UUID_TO_BIN(?)",
				Long.class, saleId.toString());
			if (saleInternalId == null) throw new IllegalStateException("No se pudo crear la venta.");

			for (PreparedItem item : prepared) {
				applySaleItem(saleInternalId, saleId, branch, item, actor);
			}
			return new PosSaleResult(findSaleByInternalId(saleInternalId), false);
		});
	}

	private PreparedItem prepareItem(Branch branch, PosSaleLine line) {
		LockedVariant variant = jdbcTemplate.query("""
			SELECT variant.id variant_internal_id,
				BIN_TO_UUID(product.public_id) product_public_id,
				BIN_TO_UUID(variant.public_id) variant_public_id,
				product.name product_name, variant.sku, variant.size_value,
				variant.color_value, variant.price,
				(product.status = 'PUBLISHED' AND category.status = 'ACTIVE'
					AND variant.status = 'ACTIVE') sellable
			FROM product_variants variant
			JOIN products product ON product.id = variant.product_id
			JOIN categories category ON category.id = product.category_id
			WHERE variant.public_id = UUID_TO_BIN(?)
			FOR UPDATE
			""", (rs, rowNum) -> new LockedVariant(
			rs.getLong("variant_internal_id"),
			UUID.fromString(rs.getString("product_public_id")),
			UUID.fromString(rs.getString("variant_public_id")),
			rs.getString("product_name"), rs.getString("sku"),
			nullableOption(rs.getString("size_value")), nullableOption(rs.getString("color_value")),
			rs.getBigDecimal("price").setScale(2, RoundingMode.UNNECESSARY),
			rs.getBoolean("sellable")), line.variantId().toString()).stream().findFirst()
			.orElseThrow(() -> notFound("La variante ya no existe."));
		if (!variant.sellable()) throw invalid("El producto " + variant.productName() + " no está disponible para la venta.");

		BigDecimal reserved = BigDecimal.ZERO.setScale(3);
		if (branch.defaultBranch()) {
			reserved = jdbcTemplate.query("""
				SELECT quantity FROM inventory_reservations
				WHERE variant_id = ? AND status = 'ACTIVE'
					AND expires_at > UTC_TIMESTAMP(6)
				FOR UPDATE
				""", (rs, rowNum) -> rs.getBigDecimal("quantity"), variant.internalId())
				.stream().reduce(BigDecimal.ZERO.setScale(3), BigDecimal::add);
		}

		jdbcTemplate.update("""
			INSERT IGNORE INTO branch_inventory_balances (branch_id, variant_id, quantity)
			VALUES (?, ?, 0.000)
			""", branch.internalId(), variant.internalId());
		BigDecimal branchBefore = jdbcTemplate.queryForObject("""
			SELECT quantity FROM branch_inventory_balances
			WHERE branch_id = ? AND variant_id = ? FOR UPDATE
			""", BigDecimal.class, branch.internalId(), variant.internalId());
		if (branchBefore == null) throw new IllegalStateException("No se pudo leer el stock de la sucursal.");
		branchBefore = canonical(branchBefore);
		BigDecimal available = branchBefore.subtract(reserved).max(BigDecimal.ZERO.setScale(3));
		if (line.quantity().compareTo(available) > 0) {
			throw conflict("Stock insuficiente para " + variant.productName() + " en " + branch.name() + ".");
		}

		jdbcTemplate.update("""
			INSERT IGNORE INTO inventory_balances (variant_id, quantity) VALUES (?, 0.000)
			""", variant.internalId());
		BigDecimal totalBefore = jdbcTemplate.queryForObject("""
			SELECT quantity FROM inventory_balances WHERE variant_id = ? FOR UPDATE
			""", BigDecimal.class, variant.internalId());
		if (totalBefore == null) throw new IllegalStateException("No se pudo leer el stock total.");
		totalBefore = canonical(totalBefore);
		if (line.quantity().compareTo(totalBefore) > 0) {
			throw conflict("Stock total insuficiente para " + variant.productName() + ".");
		}

		BigDecimal branchAfter = canonical(branchBefore.subtract(line.quantity()));
		BigDecimal totalAfter = canonical(totalBefore.subtract(line.quantity()));
		BigDecimal lineTotal = variant.price().multiply(line.quantity()).setScale(2, RoundingMode.UNNECESSARY);
		return new PreparedItem(variant, line.quantity(), branchBefore, branchAfter, totalAfter, lineTotal);
	}

	private void applySaleItem(
			long saleInternalId,
			UUID saleId,
			Branch branch,
			PreparedItem item,
			InventoryActor actor) {
		jdbcTemplate.update("""
			UPDATE branch_inventory_balances
			SET quantity = ?, version = version + 1
			WHERE branch_id = ? AND variant_id = ?
			""", item.branchAfter(), branch.internalId(), item.variant().internalId());
		jdbcTemplate.update("""
			UPDATE inventory_balances SET quantity = ?, version = version + 1
			WHERE variant_id = ?
			""", item.totalAfter(), item.variant().internalId());
		Long balanceVersion = jdbcTemplate.queryForObject(
			"SELECT version FROM inventory_balances WHERE variant_id = ?",
			Long.class, item.variant().internalId());
		if (balanceVersion == null) throw new IllegalStateException("No se pudo leer la versión del stock.");

		jdbcTemplate.update("""
			INSERT INTO pos_sale_items (
				sale_id, product_public_id, variant_id, variant_public_id,
				product_name, sku_snapshot, size_snapshot, color_snapshot,
				unit_price, quantity, line_total
			) VALUES (?, UUID_TO_BIN(?), ?, UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, ?)
			""",
			saleInternalId, item.variant().productId().toString(), item.variant().internalId(),
			item.variant().variantId().toString(), item.variant().productName(), item.variant().sku(),
			emptyOption(item.variant().size()), emptyOption(item.variant().color()),
			item.variant().price(), item.quantity(), item.lineTotal());

		jdbcTemplate.update("""
			INSERT INTO inventory_movements (
				public_id, variant_id, order_id, pos_sale_id, branch_id, idempotency_key,
				direction, quantity, delta_quantity, quantity_before, quantity_after,
				balance_version, reason, note, actor_public_id, actor_display_name
			) VALUES (
				UUID_TO_BIN(?), ?, NULL, ?, ?, UUID_TO_BIN(?),
				'DECREASE', ?, ?, ?, ?, ?, 'LOCAL_SALE', ?, UUID_TO_BIN(?), ?
			)
			""",
			UUID.randomUUID().toString(), item.variant().internalId(), saleInternalId, branch.internalId(),
			UUID.randomUUID().toString(), item.quantity(), item.quantity().negate(),
			item.branchBefore(), item.branchAfter(), balanceVersion,
			"Venta local " + saleId, actor.id().toString(), actor.displayName());
	}

	private StoredSale findByIdempotencyKey(UUID idempotencyKey) {
		return jdbcTemplate.query("""
			SELECT id, request_fingerprint FROM pos_sales
			WHERE idempotency_key = UUID_TO_BIN(?)
			""", (rs, rowNum) -> new StoredSale(rs.getLong("id"), rs.getBytes("request_fingerprint")),
			idempotencyKey.toString()).stream().findFirst().orElse(null);
	}

	private PosSaleView findSaleByInternalId(long saleInternalId) {
		return jdbcTemplate.query("""
			SELECT sale.id, BIN_TO_UUID(sale.public_id) public_id,
				BIN_TO_UUID(branch.public_id) branch_public_id, branch.name branch_name,
				sale.seller_display_name, sale.payment_method, sale.currency_code,
				sale.subtotal, sale.created_at
			FROM pos_sales sale
			JOIN store_branches branch ON branch.id = sale.branch_id
			WHERE sale.id = ?
			""", (rs, rowNum) -> mapSale(
			rs.getLong("id"), UUID.fromString(rs.getString("public_id")),
			UUID.fromString(rs.getString("branch_public_id")), rs.getString("branch_name"),
			rs.getString("seller_display_name"), PosPaymentMethod.valueOf(rs.getString("payment_method")),
			rs.getString("currency_code"), rs.getBigDecimal("subtotal"),
			rs.getTimestamp("created_at").toInstant()), saleInternalId).stream().findFirst()
			.orElseThrow(() -> notFound("La venta no existe."));
	}

	private PosSaleView mapSale(
			long internalId,
			UUID id,
			UUID branchId,
			String branchName,
			String sellerDisplayName,
			PosPaymentMethod paymentMethod,
			String currencyCode,
			BigDecimal subtotal,
			Instant createdAt) {
		List<PosSaleItemView> items = jdbcTemplate.query("""
			SELECT BIN_TO_UUID(variant_public_id) variant_public_id,
				product_name, sku_snapshot, size_snapshot, color_snapshot,
				unit_price, quantity, line_total
			FROM pos_sale_items WHERE sale_id = ? ORDER BY id
			""", (rs, rowNum) -> new PosSaleItemView(
			UUID.fromString(rs.getString("variant_public_id")), rs.getString("product_name"),
			rs.getString("sku_snapshot"), nullableOption(rs.getString("size_snapshot")),
			nullableOption(rs.getString("color_snapshot")), rs.getBigDecimal("unit_price"),
			rs.getBigDecimal("quantity"), rs.getBigDecimal("line_total")), internalId);
		return new PosSaleView(id, branchId, branchName, sellerDisplayName, paymentMethod,
			currencyCode, subtotal, items, createdAt);
	}

	private Branch requireActiveBranch(UUID branchId, boolean lock) {
		String sql = """
			SELECT id, name, is_default FROM store_branches
			WHERE public_id = UUID_TO_BIN(?) AND active = TRUE
			""" + (lock ? " FOR UPDATE" : "");
		return jdbcTemplate.query(sql, (rs, rowNum) -> new Branch(
			rs.getLong("id"), rs.getString("name"), rs.getBoolean("is_default")),
			branchId.toString()).stream().findFirst()
			.orElseThrow(() -> notFound("La sucursal no existe o está inactiva."));
	}

	private List<PosSaleLine> normalizeItems(List<PosSaleLine> rawItems) {
		if (rawItems == null || rawItems.isEmpty()) throw invalid("Agregá al menos un producto a la venta.");
		if (rawItems.size() > 100) throw invalid("La venta no puede superar 100 variantes.");
		Set<UUID> variants = new HashSet<>();
		return rawItems.stream().map(line -> {
			if (line == null || line.variantId() == null) throw invalid("La variante es obligatoria.");
			if (!variants.add(line.variantId())) throw invalid("Una variante no puede repetirse en la venta.");
			BigDecimal quantity = canonical(line.quantity());
			if (quantity.signum() <= 0 || quantity.compareTo(MAX_QUANTITY) > 0) {
				throw invalid("La cantidad debe ser mayor que cero.");
			}
			if (quantity.stripTrailingZeros().scale() > 0) {
				throw invalid("Las ventas en local requieren unidades enteras.");
			}
			return new PosSaleLine(line.variantId(), quantity);
		}).toList();
	}

	private byte[] fingerprint(UUID branchId, PosPaymentMethod paymentMethod, List<PosSaleLine> items) {
		StringBuilder canonical = new StringBuilder(branchId.toString())
			.append('|').append(paymentMethod.name()).append('|');
		items.stream().sorted(Comparator.comparing(line -> line.variantId().toString()))
			.forEach(line -> canonical.append(line.variantId()).append(':')
				.append(line.quantity().toPlainString()).append(';'));
		try {
			return MessageDigest.getInstance("SHA-256")
				.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 no está disponible.", exception);
		}
	}

	private String normalizeQuery(String value) {
		if (value == null || value.isBlank()) return null;
		String normalized = value.trim().toLowerCase();
		return normalized.length() > 100 ? normalized.substring(0, 100) : normalized;
	}

	private BigDecimal canonical(BigDecimal value) {
		if (value == null) throw invalid("La cantidad es obligatoria.");
		try {
			return value.setScale(3, RoundingMode.UNNECESSARY);
		}
		catch (ArithmeticException exception) {
			throw invalid("La cantidad admite como máximo tres decimales.");
		}
	}

	private String nullableOption(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private String emptyOption(String value) {
		return value == null ? "" : value;
	}

	private PosSaleException invalid(String message) {
		return new PosSaleException(Failure.INVALID, message);
	}

	private PosSaleException notFound(String message) {
		return new PosSaleException(Failure.NOT_FOUND, message);
	}

	private PosSaleException conflict(String message) {
		return new PosSaleException(Failure.CONFLICT, message);
	}

	public enum PosPaymentMethod {
		CASH,
		BANK_TRANSFER,
		CARD,
		OTHER
	}

	public enum Failure {
		INVALID,
		NOT_FOUND,
		CONFLICT
	}

	public static final class PosSaleException extends RuntimeException {
		private final Failure failure;

		public PosSaleException(Failure failure, String message) {
			super(message);
			this.failure = failure;
		}

		public Failure failure() {
			return failure;
		}
	}

	public record PosCatalogItem(
		UUID variantId,
		String productName,
		String sku,
		String size,
		String color,
		BigDecimal unitPrice,
		BigDecimal availableQuantity) {}

	public record PosSaleLine(UUID variantId, BigDecimal quantity) {}

	public record PosSaleItemView(
		UUID variantId,
		String productName,
		String sku,
		String size,
		String color,
		BigDecimal unitPrice,
		BigDecimal quantity,
		BigDecimal lineTotal) {}

	public record PosSaleView(
		UUID id,
		UUID branchId,
		String branchName,
		String sellerDisplayName,
		PosPaymentMethod paymentMethod,
		String currencyCode,
		BigDecimal subtotal,
		List<PosSaleItemView> items,
		Instant createdAt) {}

	public record PosSaleResult(PosSaleView sale, boolean replay) {}

	private record Branch(long internalId, String name, boolean defaultBranch) {}
	private record StoredSale(long internalId, byte[] fingerprint) {}
	private record LockedVariant(
		long internalId,
		UUID productId,
		UUID variantId,
		String productName,
		String sku,
		String size,
		String color,
		BigDecimal price,
		boolean sellable) {}
	private record PreparedItem(
		LockedVariant variant,
		BigDecimal quantity,
		BigDecimal branchBefore,
		BigDecimal branchAfter,
		BigDecimal totalAfter,
		BigDecimal lineTotal) {}
}
