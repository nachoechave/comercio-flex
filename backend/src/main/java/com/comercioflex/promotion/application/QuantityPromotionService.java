package com.comercioflex.promotion.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuantityPromotionService {

  private static final Set<String> SCOPES = Set.of("PRODUCT", "PRODUCTS", "CATEGORY");
  private final JdbcTemplate jdbc;

  public QuantityPromotionService(@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<View> list() {
    return load(null);
  }

  public List<View> active(Instant at) {
    return load(at == null ? Instant.now() : at);
  }

  private List<View> load(Instant activeAt) {
    String activeWhere = activeAt == null ? "" : """
          WHERE promo.active = TRUE
            AND (promo.starts_at IS NULL OR promo.starts_at <= ?)
            AND (promo.ends_at IS NULL OR promo.ends_at > ?)
        """;
    String sql = """
        SELECT promo.id internal_id,
               BIN_TO_UUID(promo.public_id) id,
               promo.scope_type,
               BIN_TO_UUID(product.public_id) product_id,
               product.name product_name,
               category.id category_internal_id,
               BIN_TO_UUID(category.public_id) category_id,
               category.name category_name,
               promo.name,
               promo.bundle_quantity,
               promo.bundle_price,
               promo.active,
               promo.starts_at,
               promo.ends_at,
               promo.version
        FROM quantity_promotions promo
        LEFT JOIN products product ON product.id = promo.product_id
        LEFT JOIN categories category ON category.id = promo.category_id
        %s
        ORDER BY promo.active DESC, promo.updated_at DESC, promo.id DESC
        """.formatted(activeWhere);

    Object[] args = activeAt == null
        ? new Object[0]
        : new Object[] { Timestamp.from(activeAt), Timestamp.from(activeAt) };

    List<BaseRow> rows = jdbc.query(sql, (rs, row) -> new BaseRow(
        rs.getLong("internal_id"),
        UUID.fromString(rs.getString("id")),
        rs.getString("scope_type"),
        uuid(rs.getString("product_id")),
        rs.getString("product_name"),
        nullableLong(rs.getObject("category_internal_id")),
        uuid(rs.getString("category_id")),
        rs.getString("category_name"),
        rs.getString("name"),
        rs.getInt("bundle_quantity"),
        rs.getBigDecimal("bundle_price"),
        rs.getBoolean("active"),
        instant(rs.getTimestamp("starts_at")),
        instant(rs.getTimestamp("ends_at")),
        rs.getLong("version")), args);

    List<View> result = new ArrayList<>();
    for (BaseRow row : rows) {
      List<UUID> productIds = eligibleProductIds(row, activeAt != null);
      if (activeAt != null && productIds.isEmpty()) continue;
      result.add(new View(
          row.id(),
          row.scopeType(),
          row.productId(),
          row.productName(),
          row.categoryId(),
          row.categoryName(),
          productIds,
          row.name(),
          row.bundleQuantity(),
          row.bundlePrice(),
          row.active(),
          row.startsAt(),
          row.endsAt(),
          row.version()));
    }
    return result;
  }

  private List<UUID> eligibleProductIds(BaseRow row, boolean publicOnly) {
    if ("CATEGORY".equals(row.scopeType())) {
      if (row.categoryInternalId() == null) return List.of();
      String published = publicOnly ? " AND product.status = 'PUBLISHED' AND category.status = 'ACTIVE'" : "";
      return jdbc.query("""
          SELECT BIN_TO_UUID(product.public_id)
          FROM products product
          JOIN categories category ON category.id = product.category_id
          WHERE product.category_id = ?%s
          ORDER BY product.id
          """.formatted(published),
          (rs, index) -> UUID.fromString(rs.getString(1)),
          row.categoryInternalId());
    }

    if ("PRODUCTS".equals(row.scopeType())) {
      String published = publicOnly
          ? " AND product.status = 'PUBLISHED' AND category.status = 'ACTIVE'"
          : "";
      return jdbc.query("""
          SELECT BIN_TO_UUID(product.public_id)
          FROM quantity_promotion_products target
          JOIN products product ON product.id = target.product_id
          JOIN categories category ON category.id = product.category_id
          WHERE target.promotion_id = ?%s
          ORDER BY product.id
          """.formatted(published),
          (rs, index) -> UUID.fromString(rs.getString(1)),
          row.internalId());
    }

    if (row.productId() == null) return List.of();
    if (!publicOnly) return List.of(row.productId());
    Integer sellable = jdbc.queryForObject("""
        SELECT COUNT(*)
        FROM products product
        JOIN categories category ON category.id = product.category_id
        WHERE product.public_id = UUID_TO_BIN(?)
          AND product.status = 'PUBLISHED'
          AND category.status = 'ACTIVE'
        """, Integer.class, row.productId().toString());
    return sellable != null && sellable > 0 ? List.of(row.productId()) : List.of();
  }

  public View create(Command raw) {
    Command command = validate(raw);
    Target target = resolveTarget(command);
    UUID id = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO quantity_promotions
          (public_id, scope_type, product_id, category_id, name, bundle_quantity, bundle_price,
           active, starts_at, ends_at)
        VALUES (UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id.toString(),
        command.scopeType(),
        target.productInternalId(),
        target.categoryInternalId(),
        command.name(),
        command.bundleQuantity(),
        command.bundlePrice(),
        command.active(),
        timestamp(command.startsAt()),
        timestamp(command.endsAt()));
    Long promotionInternalId = jdbc.queryForObject(
        "SELECT id FROM quantity_promotions WHERE public_id = UUID_TO_BIN(?)",
        Long.class, id.toString());
    replaceProductTargets(promotionInternalId, target.productInternalIds());
    return find(id);
  }

  public View update(UUID id, Command raw, long version) {
    Command command = validate(raw);
    Target target = resolveTarget(command);
    Long promotionInternalId = jdbc.query("""
        SELECT id FROM quantity_promotions WHERE public_id = UUID_TO_BIN(?)
        """, (rs, row) -> rs.getLong(1), id.toString()).stream().findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontramos la promoción."));

    int changed = jdbc.update("""
        UPDATE quantity_promotions
        SET scope_type = ?, product_id = ?, category_id = ?, name = ?, bundle_quantity = ?,
            bundle_price = ?, active = ?, starts_at = ?, ends_at = ?, version = version + 1
        WHERE public_id = UUID_TO_BIN(?) AND version = ?
        """,
        command.scopeType(),
        target.productInternalId(),
        target.categoryInternalId(),
        command.name(),
        command.bundleQuantity(),
        command.bundlePrice(),
        command.active(),
        timestamp(command.startsAt()),
        timestamp(command.endsAt()),
        id.toString(),
        version);
    if (changed == 0) {
      if (!exists(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontramos la promoción.");
      throw new ResponseStatusException(HttpStatus.CONFLICT, "La promoción cambió. Actualizá la pantalla e intentá nuevamente.");
    }
    replaceProductTargets(promotionInternalId, target.productInternalIds());
    return find(id);
  }

  public View setActive(UUID id, boolean active, long version) {
    int changed = jdbc.update("""
        UPDATE quantity_promotions
        SET active = ?, version = version + 1
        WHERE public_id = UUID_TO_BIN(?) AND version = ?
        """, active, id.toString(), version);
    if (changed == 0) {
      if (!exists(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontramos la promoción.");
      throw new ResponseStatusException(HttpStatus.CONFLICT, "La promoción cambió. Actualizá la pantalla e intentá nuevamente.");
    }
    return find(id);
  }

  private Target resolveTarget(Command command) {
    if ("CATEGORY".equals(command.scopeType())) {
      Long categoryId = jdbc.query("""
          SELECT id FROM categories WHERE public_id = UUID_TO_BIN(?)
          """, (rs, row) -> rs.getLong(1), command.categoryId().toString()).stream().findFirst()
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontramos la categoría."));
      return new Target(null, categoryId, List.of());
    }

    List<UUID> requested = "PRODUCT".equals(command.scopeType())
        ? List.of(command.productId())
        : command.productIds();
    List<Long> internalIds = new ArrayList<>();
    for (UUID productId : requested) {
      Long internal = jdbc.query("""
          SELECT id FROM products WHERE public_id = UUID_TO_BIN(?)
          """, (rs, row) -> rs.getLong(1), productId.toString()).stream().findFirst()
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontramos uno de los productos."));
      internalIds.add(internal);
    }
    return new Target(
        "PRODUCT".equals(command.scopeType()) ? internalIds.get(0) : null,
        null,
        internalIds);
  }

  private void replaceProductTargets(Long promotionInternalId, List<Long> productInternalIds) {
    jdbc.update("DELETE FROM quantity_promotion_products WHERE promotion_id = ?", promotionInternalId);
    for (Long productInternalId : productInternalIds) {
      jdbc.update("""
          INSERT INTO quantity_promotion_products (promotion_id, product_id)
          VALUES (?, ?)
          """, promotionInternalId, productInternalId);
    }
  }

  private View find(UUID id) {
    return list().stream().filter(item -> item.id().equals(id)).findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontramos la promoción."));
  }

  private boolean exists(UUID id) {
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM quantity_promotions WHERE public_id = UUID_TO_BIN(?)",
        Integer.class, id.toString());
    return count != null && count > 0;
  }

  private Command validate(Command raw) {
    if (raw == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Completá los datos de la promoción.");
    }
    String scope = raw.scopeType() == null || raw.scopeType().isBlank()
        ? "PRODUCT"
        : raw.scopeType().trim().toUpperCase();
    if (!SCOPES.contains(scope)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El alcance de la promoción no es válido.");
    }

    UUID productId = raw.productId();
    List<UUID> productIds = raw.productIds() == null
        ? List.of()
        : new ArrayList<>(new LinkedHashSet<>(raw.productIds().stream().filter(java.util.Objects::nonNull).toList()));
    UUID categoryId = raw.categoryId();

    if ("PRODUCT".equals(scope) && productId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí un producto.");
    }
    if ("PRODUCTS".equals(scope) && productIds.size() < 2) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí al menos dos productos.");
    }
    if ("CATEGORY".equals(scope) && categoryId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí una categoría.");
    }

    String name = raw.name() == null ? "" : raw.name().trim();
    if (name.isEmpty() || name.length() > 120) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre debe tener entre 1 y 120 caracteres.");
    }
    if (raw.bundleQuantity() < 2 || raw.bundleQuantity() > 99) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe estar entre 2 y 99.");
    }
    if (raw.bundlePrice() == null || raw.bundlePrice().compareTo(BigDecimal.ZERO) <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El precio promocional debe ser mayor a cero.");
    }

    BigDecimal price;
    try {
      price = raw.bundlePrice().setScale(2, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El precio admite hasta 2 decimales.");
    }
    if (raw.startsAt() != null && raw.endsAt() != null && !raw.endsAt().isAfter(raw.startsAt())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de finalización debe ser posterior al inicio.");
    }

    return new Command(scope, productId, productIds, categoryId, name, raw.bundleQuantity(), price,
        raw.active(), raw.startsAt(), raw.endsAt());
  }

  private static Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  private static UUID uuid(String value) {
    return value == null ? null : UUID.fromString(value);
  }

  private static Long nullableLong(Object value) {
    return value == null ? null : ((Number) value).longValue();
  }

  private record BaseRow(
      long internalId,
      UUID id,
      String scopeType,
      UUID productId,
      String productName,
      Long categoryInternalId,
      UUID categoryId,
      String categoryName,
      String name,
      int bundleQuantity,
      BigDecimal bundlePrice,
      boolean active,
      Instant startsAt,
      Instant endsAt,
      long version) {}

  private record Target(Long productInternalId, Long categoryInternalId, List<Long> productInternalIds) {}

  public record Command(
      String scopeType,
      UUID productId,
      List<UUID> productIds,
      UUID categoryId,
      String name,
      int bundleQuantity,
      BigDecimal bundlePrice,
      boolean active,
      Instant startsAt,
      Instant endsAt) {}

  public record View(
      UUID id,
      String scopeType,
      UUID productId,
      String productName,
      UUID categoryId,
      String categoryName,
      List<UUID> productIds,
      String name,
      int bundleQuantity,
      BigDecimal bundlePrice,
      boolean active,
      Instant startsAt,
      Instant endsAt,
      long version) {}
}
