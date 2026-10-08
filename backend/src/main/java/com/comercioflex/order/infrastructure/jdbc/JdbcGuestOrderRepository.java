package com.comercioflex.order.infrastructure.jdbc;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.ArrayList;
import java.util.UUID;
import com.comercioflex.order.domain.OrderPaymentMethod;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import com.comercioflex.order.application.OrderPaymentPricing;
import com.comercioflex.order.application.ActiveQuantityPromotion;

import com.comercioflex.order.application.GuestOrderRepository;
import com.comercioflex.order.application.LockedOrderVariant;
import com.comercioflex.catalog.domain.SaleUnit;
import com.comercioflex.catalog.domain.SaleQuantityRules;
import com.comercioflex.order.application.ReservedOrderItem;
import com.comercioflex.order.application.StoredGuestOrder;
import com.comercioflex.order.domain.FulfillmentType;
import com.comercioflex.order.domain.GuestOrder;
import com.comercioflex.order.domain.GuestOrderItem;
import com.comercioflex.order.domain.OrderStatus;
import com.comercioflex.catalog.domain.VariantOptionValue;

@Repository
public class JdbcGuestOrderRepository implements GuestOrderRepository {

	private static final String ORDER_SELECT = """
			SELECT
					order_record.id order_internal_id,
					BIN_TO_UUID(order_record.public_id) order_public_id,
					order_record.status,
					order_record.fulfillment_type,
					order_record.payment_method,
					order_record.customer_name,
					order_record.customer_phone,
					order_record.customer_email,
					order_record.customer_notes,
					order_record.currency_code,
					order_record.list_subtotal,
					order_record.discount_percentage,
					order_record.discount_amount,
					order_record.subtotal, order_record.shipping_amount, order_record.shipping_snapshot,
					order_record.reservation_expires_at,
					order_record.created_at
			FROM orders order_record
			""";

	private final JdbcTemplate jdbcTemplate;
	private final OrderOptionsJsonCodec optionsJsonCodec;

	public JdbcGuestOrderRepository(
			@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate,
			OrderOptionsJsonCodec optionsJsonCodec) {
		this.jdbcTemplate = jdbcTemplate;
		this.optionsJsonCodec = optionsJsonCodec;
	}

	@Override
	public Optional<StoredGuestOrder> findByIdempotencyKey(UUID idempotencyKey) {
		return jdbcTemplate.query("""
			SELECT id, request_fingerprint
			FROM orders
			WHERE idempotency_key = UUID_TO_BIN(?)
			""",
			(resultSet, rowNumber) -> new StoredGuestOrder(
				resultSet.getBytes("request_fingerprint"),
				findByInternalId(resultSet.getLong("id"))),
			idempotencyKey.toString())
			.stream()
			.findFirst();
	}

	@Override
	public Optional<LockedOrderVariant> lockVariant(UUID variantId) {
		Optional<LockedOrderVariant> lockedVariant = jdbcTemplate.query("""
			SELECT
				variant.id variant_internal_id,
				BIN_TO_UUID(product.public_id) product_public_id,
				BIN_TO_UUID(variant.public_id) variant_public_id,
				product.name product_name,
                product.sale_unit, product.sale_minimum, product.sale_step, product.sale_maximum,
				variant.sku,
				variant.size_value,
				variant.color_value,
				variant.price,
				COALESCE(balance.quantity, 0.000) physical_quantity,
				(
					product.status = 'PUBLISHED'
					AND category.status = 'ACTIVE'
					AND variant.status = 'ACTIVE'
				) sellable
			FROM product_variants variant
			JOIN products product ON product.id = variant.product_id
			JOIN categories category ON category.id = product.category_id
			LEFT JOIN inventory_balances balance ON balance.variant_id = variant.id
			WHERE variant.public_id = UUID_TO_BIN(?)
			FOR UPDATE
			""",
			(resultSet, rowNumber) -> new LockedOrderVariant(
				resultSet.getLong("variant_internal_id"),
				UUID.fromString(resultSet.getString("product_public_id")),
				UUID.fromString(resultSet.getString("variant_public_id")),
				resultSet.getString("product_name"),
				resultSet.getString("sku"),
				nullableOption(resultSet.getString("size_value")),
				nullableOption(resultSet.getString("color_value")),
				List.of(),
				resultSet.getBigDecimal("price"),
				resultSet.getBigDecimal("physical_quantity"),
				BigDecimal.ZERO.setScale(3),
				resultSet.getBoolean("sellable"),
                new SaleQuantityRules(SaleUnit.valueOf(resultSet.getString("sale_unit")),
                    resultSet.getBigDecimal("sale_minimum"), resultSet.getBigDecimal("sale_step"),
                    resultSet.getBigDecimal("sale_maximum"))),
			variantId.toString())
			.stream()
			.findFirst();
		if (lockedVariant.isEmpty()) {
			return Optional.empty();
		}

		LockedOrderVariant variant = lockedVariant.get();
		List<VariantOptionValue> options = jdbcTemplate.query("""
			SELECT product_option.name, option_value.value
			FROM product_variant_option_values relation
			JOIN product_option_values option_value ON option_value.id = relation.option_value_id
			JOIN product_options product_option ON product_option.id = option_value.option_id
			WHERE relation.variant_id = ?
			ORDER BY product_option.position, option_value.position
			""",
			(resultSet, rowNumber) -> new VariantOptionValue(
				resultSet.getString("name"), resultSet.getString("value")),
			variant.internalId());
		BigDecimal reservedQuantity = jdbcTemplate.query("""
			SELECT quantity
			FROM inventory_reservations
			WHERE variant_id = ?
				AND status = 'ACTIVE'
				AND expires_at > UTC_TIMESTAMP(6)
			FOR UPDATE
			""",
			(resultSet, rowNumber) -> resultSet.getBigDecimal("quantity"),
			variant.internalId())
			.stream()
			.reduce(BigDecimal.ZERO.setScale(3), BigDecimal::add);
		return Optional.of(new LockedOrderVariant(
			variant.internalId(),
			variant.productId(),
			variant.variantId(),
			variant.productName(),
			variant.sku(),
			variant.size(),
			variant.color(),
			List.copyOf(options),
			variant.unitPrice(),
			variant.physicalQuantity(),
			reservedQuantity,
			variant.sellable(), variant.saleRules()));
	}

	@Override
	public String findCurrencyCode() {
		String currency = jdbcTemplate.queryForObject("""
			SELECT currency_code
			FROM store_settings
			LIMIT 1
			""",
			String.class);
		if (currency == null) {
			throw new IllegalStateException("No se pudo obtener la moneda del comercio.");
		}
		return currency;
	}

	@Override
	public OrderPaymentPricing findPaymentPricing() {
			return jdbcTemplate.query("""
					SELECT bank_transfer_enabled, bank_transfer_discount_percentage
					FROM store_settings
					LIMIT 1
					""",
					(resultSet, rowNumber) -> new OrderPaymentPricing(
							resultSet.getBoolean("bank_transfer_enabled"),
							resultSet.getBigDecimal("bank_transfer_discount_percentage")))
					.stream()
					.findFirst()
					.orElse(new OrderPaymentPricing(false, BigDecimal.ZERO));
	}

	@Override
	public List<ActiveQuantityPromotion> findActiveQuantityPromotions(Set<UUID> productIds, Instant at) {
		if (productIds == null || productIds.isEmpty()) return List.of();
		String placeholders = String.join(",", java.util.Collections.nCopies(productIds.size(), "UUID_TO_BIN(?)"));
		record PromotionRow(
				long internalId,
				UUID promotionId,
				String scopeType,
				UUID productId,
				Long categoryInternalId,
				int bundleQuantity,
				BigDecimal bundlePrice) {}

		List<PromotionRow> rows = jdbcTemplate.query("""
			SELECT promo.id internal_id,
			       BIN_TO_UUID(promo.public_id) promotion_public_id,
			       promo.scope_type,
			       BIN_TO_UUID(product.public_id) product_public_id,
			       promo.category_id category_internal_id,
			       promo.bundle_quantity,
			       promo.bundle_price
			FROM quantity_promotions promo
			LEFT JOIN products product ON product.id = promo.product_id
			WHERE promo.active = TRUE
			  AND (promo.starts_at IS NULL OR promo.starts_at <= ?)
			  AND (promo.ends_at IS NULL OR promo.ends_at > ?)
			ORDER BY promo.starts_at DESC, promo.updated_at DESC, promo.id DESC
			""",
			(resultSet, rowNumber) -> new PromotionRow(
				resultSet.getLong("internal_id"),
				UUID.fromString(resultSet.getString("promotion_public_id")),
				resultSet.getString("scope_type"),
				resultSet.getString("product_public_id") == null
					? null : UUID.fromString(resultSet.getString("product_public_id")),
				resultSet.getObject("category_internal_id") == null
					? null : resultSet.getLong("category_internal_id"),
				resultSet.getInt("bundle_quantity"),
				resultSet.getBigDecimal("bundle_price")),
			Timestamp.from(at),
			Timestamp.from(at));

		List<ActiveQuantityPromotion> result = new ArrayList<>();
		for (PromotionRow row : rows) {
			List<UUID> eligible = new ArrayList<>();
            List<UUID> second = new ArrayList<>();
			if ("PRODUCT".equals(row.scopeType())) {
				if (row.productId() != null && productIds.contains(row.productId())) {
					eligible.add(row.productId());
				}
			} else if ("PRODUCTS".equals(row.scopeType())) {
				String sql = """
					SELECT BIN_TO_UUID(product.public_id)
					FROM quantity_promotion_products target
					JOIN products product ON product.id = target.product_id
					WHERE target.promotion_id = ?
					  AND product.public_id IN (%s)
					""".formatted(placeholders);
				List<Object> args = new ArrayList<>();
				args.add(row.internalId());
				productIds.stream().map(UUID::toString).forEach(args::add);
				eligible.addAll(jdbcTemplate.query(sql,
					(rs, n) -> UUID.fromString(rs.getString(1)), args.toArray()));
			} else if ("COMBO".equals(row.scopeType())) {
                for (int group = 1; group <= 2; group++) {
                    List<UUID> targets = jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(product.public_id)
                        FROM quantity_promotion_products target
                        JOIN products product ON product.id = target.product_id
                        JOIN categories category ON category.id = product.category_id
                        WHERE target.promotion_id = ? AND target.group_number = ?
                          AND product.status = 'PUBLISHED' AND category.status = 'ACTIVE'
                        """, (rs, n) -> UUID.fromString(rs.getString(1)), row.internalId(), group);
                    (group == 1 ? eligible : second).addAll(targets);
                }
                if (second.isEmpty()) continue;
            } else if ("CATEGORY".equals(row.scopeType()) && row.categoryInternalId() != null) {
				String sql = """
					SELECT BIN_TO_UUID(product.public_id)
					FROM products product
					JOIN categories category ON category.id = product.category_id
					WHERE product.category_id = ?
					  AND product.public_id IN (%s)
					  AND product.status = 'PUBLISHED'
					  AND category.status = 'ACTIVE'
					""".formatted(placeholders);
				List<Object> args = new ArrayList<>();
				args.add(row.categoryInternalId());
				productIds.stream().map(UUID::toString).forEach(args::add);
				eligible.addAll(jdbcTemplate.query(sql,
					(rs, n) -> UUID.fromString(rs.getString(1)), args.toArray()));
			}
			if (!eligible.isEmpty()) {
				result.add(new ActiveQuantityPromotion(
					row.promotionId(), eligible, second, row.bundleQuantity(), row.bundlePrice()));
			}
		}
		return result;
	}

	@Override
	public long insertOrder(
			UUID orderId,
			UUID idempotencyKey,
			byte[] requestFingerprint,
			byte[] lookupTokenHash,
			String customerName,
			String customerPhone,
			String customerEmail,
			String notes,
			String currencyCode,
			OrderPaymentMethod paymentMethod,
			BigDecimal listSubtotal,
			BigDecimal discountPercentage,
			BigDecimal discountAmount,
			BigDecimal subtotal,
			Instant reservationExpiresAt) {

			KeyHolder keyHolder = new GeneratedKeyHolder();

			jdbcTemplate.update(connection -> {
					PreparedStatement statement = connection.prepareStatement("""
							INSERT INTO orders (
									public_id,
									idempotency_key,
									request_fingerprint,
									lookup_token_hash,
									status,
									fulfillment_type,
									payment_method,
									customer_name,
									customer_phone,
									customer_email,
									customer_notes,
									currency_code,
									list_subtotal,
									discount_percentage,
									discount_amount,
									subtotal,
									reservation_expires_at
							)
							VALUES (
									UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?,
									'PENDING_CONFIRMATION', 'PICKUP',
									?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
							)
							""",
							Statement.RETURN_GENERATED_KEYS);

					statement.setString(1, orderId.toString());
					statement.setString(2, idempotencyKey.toString());
					statement.setBytes(3, requestFingerprint);
					statement.setBytes(4, lookupTokenHash);
					statement.setString(5, paymentMethod.name());
					statement.setString(6, customerName);
					statement.setString(7, customerPhone);
					statement.setString(8, customerEmail);
					statement.setString(9, notes);
					statement.setString(10, currencyCode);
					statement.setBigDecimal(11, listSubtotal);
					statement.setBigDecimal(12, discountPercentage);
					statement.setBigDecimal(13, discountAmount);
					statement.setBigDecimal(14, subtotal);
					statement.setTimestamp(15, Timestamp.from(reservationExpiresAt));

					return statement;
			}, keyHolder);

			Number key = keyHolder.getKey();
			if (key == null) {
					throw new IllegalStateException(
							"No se pudo obtener el identificador del pedido.");
			}

			return key.longValue();
	}

	@Override
	public void insertInitialHistory(long orderInternalId) {
		jdbcTemplate.update("""
			INSERT INTO order_status_history (
				public_id,
				order_id,
				previous_status,
				new_status,
				actor_display_name
			)
			VALUES (UUID_TO_BIN(?), ?, NULL, 'PENDING_CONFIRMATION', 'Sistema')
			""",
			UUID.randomUUID().toString(),
			orderInternalId);
	}

	@Override
	public void insertItemsAndReservations(
			long orderInternalId,
			List<ReservedOrderItem> items,
			Instant reservationExpiresAt) {
		for (ReservedOrderItem item : items) {
			LockedOrderVariant variant = item.variant();
			jdbcTemplate.update("""
				INSERT INTO order_items (
					order_id,
					product_public_id,
					variant_id,
					variant_public_id,
					product_name,
					sku_snapshot,
					size_snapshot,
					color_snapshot,
					options_snapshot,
					unit_code,
					unit_price,
					quantity,
					line_total
				)
				VALUES (
					?, UUID_TO_BIN(?), ?, UUID_TO_BIN(?), ?, ?, ?, ?, ?,
					'UNIT', ?, ?, ?
				)
				""",
				orderInternalId,
				variant.productId().toString(),
				variant.internalId(),
				variant.variantId().toString(),
				variant.productName(),
				variant.sku(),
				emptyOption(variant.size()),
				emptyOption(variant.color()),
				optionsJsonCodec.write(variant.options()),
				variant.unitPrice(),
				item.quantity(),
				item.lineTotal());
			jdbcTemplate.update("""
				INSERT INTO inventory_reservations (
					public_id,
					order_id,
					variant_id,
					quantity,
					status,
					expires_at
				)
				VALUES (UUID_TO_BIN(?), ?, ?, ?, 'ACTIVE', ?)
				""",
				item.reservationId().toString(),
				orderInternalId,
				variant.internalId(),
				item.quantity(),
				Timestamp.from(reservationExpiresAt));
		}
	}

	@Override
	public GuestOrder findByInternalId(long orderInternalId) {
		return jdbcTemplate.query(
			ORDER_SELECT + " WHERE order_record.id = ?",
			(resultSet, rowNumber) -> mapOrder(resultSet),
			orderInternalId)
			.stream()
			.findFirst()
			.orElseThrow(() -> new IllegalStateException(
				"No se pudo recuperar el pedido."));
	}

	@Override
	public Optional<GuestOrder> findByPublicIdAndTokenHash(
			UUID orderId,
			byte[] lookupTokenHash) {
		return jdbcTemplate.query(
			ORDER_SELECT + """
				WHERE order_record.public_id = UUID_TO_BIN(?)
					AND order_record.lookup_token_hash = ?
				""",
			(resultSet, rowNumber) -> mapOrder(resultSet),
			orderId.toString(),
			lookupTokenHash)
			.stream()
			.findFirst();
	}

	@Override
	public void expireOrder(long orderInternalId) {
		int changed = jdbcTemplate.update("""
			UPDATE orders
			SET status = 'EXPIRED', version = version + 1
			WHERE id = ?
				AND status = 'PENDING_CONFIRMATION'
				AND NOT EXISTS (
					SELECT 1
					FROM payment_intents intent
					WHERE intent.order_id = orders.id
						AND intent.provider = 'MERCADO_PAGO'
						AND intent.status = 'PENDING'
						AND (
							(
								NOT EXISTS (
									SELECT 1 FROM payment_transactions transaction_record
									WHERE transaction_record.payment_intent_id = intent.id
										AND transaction_record.provider_status = 'PENDING'
								)
								AND DATE_ADD(intent.checkout_expires_at, INTERVAL 5 MINUTE)
									> UTC_TIMESTAMP(6)
							)
							OR (
								EXISTS (
									SELECT 1 FROM payment_transactions transaction_record
									WHERE transaction_record.payment_intent_id = intent.id
										AND transaction_record.provider_status = 'PENDING'
								)
								AND DATE_ADD(intent.checkout_expires_at, INTERVAL 24 HOUR)
									> UTC_TIMESTAMP(6)
							)
						)
				)
			""",
			orderInternalId);
		if (changed == 0) return;
		jdbcTemplate.update("""
			UPDATE inventory_reservations
			SET status = 'EXPIRED'
			WHERE order_id = ?
				AND status = 'ACTIVE'
			""",
			orderInternalId);
		jdbcTemplate.update("""
			INSERT INTO order_status_history (
				public_id, order_id, previous_status, new_status, actor_display_name
			)
			VALUES (UUID_TO_BIN(?), ?, 'PENDING_CONFIRMATION', 'EXPIRED', 'Sistema')
			""",
			UUID.randomUUID().toString(),
			orderInternalId);
	}

	private GuestOrder mapOrder(ResultSet resultSet) throws SQLException {
		long internalId = resultSet.getLong("order_internal_id");
		List<GuestOrderItem> items = jdbcTemplate.query("""
			SELECT
				BIN_TO_UUID(product_public_id) product_public_id,
				BIN_TO_UUID(variant_public_id) variant_public_id,
				product_name,
				size_snapshot,
				color_snapshot,
				options_snapshot,
				unit_code,
				unit_price,
				quantity,
				line_total
			FROM order_items
			WHERE order_id = ?
			ORDER BY id
			""",
			this::mapItem,
			internalId);
		return new GuestOrder(
				UUID.fromString(resultSet.getString("order_public_id")),
				internalId,
				OrderStatus.valueOf(resultSet.getString("status")),
				FulfillmentType.valueOf(resultSet.getString("fulfillment_type")),
				OrderPaymentMethod.valueOf(resultSet.getString("payment_method")),
				resultSet.getString("customer_name"),
				resultSet.getString("customer_phone"),
				resultSet.getString("customer_email"),
				resultSet.getString("customer_notes"),
				resultSet.getString("currency_code"),
				resultSet.getBigDecimal("list_subtotal"),
				resultSet.getBigDecimal("discount_percentage"),
				resultSet.getBigDecimal("discount_amount"),
				resultSet.getBigDecimal("subtotal"),
				resultSet.getTimestamp("reservation_expires_at").toInstant(),
				resultSet.getTimestamp("created_at").toInstant(),
				items, resultSet.getBigDecimal("shipping_amount"), readShipping(resultSet.getString("shipping_snapshot")));
	}

	private GuestOrderItem mapItem(ResultSet resultSet, int rowNumber)
			throws SQLException {
		return new GuestOrderItem(
			UUID.fromString(resultSet.getString("product_public_id")),
			UUID.fromString(resultSet.getString("variant_public_id")),
			resultSet.getString("product_name"),
			nullableOption(resultSet.getString("size_snapshot")),
			nullableOption(resultSet.getString("color_snapshot")),
			optionsJsonCodec.read(resultSet.getString("options_snapshot")),
			resultSet.getString("unit_code"),
			resultSet.getBigDecimal("unit_price"),
			resultSet.getBigDecimal("quantity"),
			resultSet.getBigDecimal("line_total"));
	}

	private String nullableOption(String value) {
		return value == null || value.isEmpty() ? null : value;
	}

	private String emptyOption(String value) {
		return value == null ? "" : value;
	}
 private com.comercioflex.shipping.domain.ShippingModels.Snapshot readShipping(String value) {
if(value==null) return null;
try { return new com.fasterxml.jackson.databind.ObjectMapper().readValue(value,com.comercioflex.shipping.domain.ShippingModels.Snapshot.class); }
catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Invalid shipping snapshot",e); }
}
}
