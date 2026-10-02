package com.comercioflex.promotion.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuantityPromotionService {

  private final JdbcTemplate jdbc;

  public QuantityPromotionService(@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<View> list() {
    return jdbc.query("""
        SELECT BIN_TO_UUID(promo.public_id) id,
               BIN_TO_UUID(product.public_id) product_id,
               product.name product_name,
               promo.name,
               promo.bundle_quantity,
               promo.bundle_price,
               promo.active,
               promo.starts_at,
               promo.ends_at,
               promo.version
        FROM quantity_promotions promo
        JOIN products product ON product.id = promo.product_id
        ORDER BY promo.active DESC, promo.updated_at DESC, promo.id DESC
        """, (rs, row) -> new View(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("product_id")),
            rs.getString("product_name"),
            rs.getString("name"),
            rs.getInt("bundle_quantity"),
            rs.getBigDecimal("bundle_price"),
            rs.getBoolean("active"),
            instant(rs.getTimestamp("starts_at")),
            instant(rs.getTimestamp("ends_at")),
            rs.getLong("version")));
  }

  public List<View> active(Instant at) {
    Instant now = at == null ? Instant.now() : at;
    return jdbc.query("""
        SELECT BIN_TO_UUID(promo.public_id) id,
               BIN_TO_UUID(product.public_id) product_id,
               product.name product_name,
               promo.name,
               promo.bundle_quantity,
               promo.bundle_price,
               promo.active,
               promo.starts_at,
               promo.ends_at,
               promo.version
        FROM quantity_promotions promo
        JOIN products product ON product.id = promo.product_id
        JOIN categories category ON category.id = product.category_id
        WHERE promo.active = TRUE
          AND product.status = 'PUBLISHED'
          AND category.status = 'ACTIVE'
          AND (promo.starts_at IS NULL OR promo.starts_at <= ?)
          AND (promo.ends_at IS NULL OR promo.ends_at > ?)
        ORDER BY product.id, promo.starts_at DESC, promo.updated_at DESC, promo.id DESC
        """,
        (rs, row) -> new View(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("product_id")),
            rs.getString("product_name"),
            rs.getString("name"),
            rs.getInt("bundle_quantity"),
            rs.getBigDecimal("bundle_price"),
            rs.getBoolean("active"),
            instant(rs.getTimestamp("starts_at")),
            instant(rs.getTimestamp("ends_at")),
            rs.getLong("version")),
        Timestamp.from(now),
        Timestamp.from(now));
  }

  public View create(Command raw) {
    Command command = validate(raw);
    UUID id = UUID.randomUUID();
    int changed = jdbc.update("""
        INSERT INTO quantity_promotions
          (public_id, product_id, name, bundle_quantity, bundle_price, active, starts_at, ends_at)
        SELECT UUID_TO_BIN(?), product.id, ?, ?, ?, ?, ?, ?
        FROM products product
        WHERE product.public_id = UUID_TO_BIN(?)
        """,
        id.toString(),
        command.name(),
        command.bundleQuantity(),
        command.bundlePrice(),
        command.active(),
        timestamp(command.startsAt()),
        timestamp(command.endsAt()),
        command.productId().toString());
    if (changed == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontramos el producto.");
    }
    return find(id);
  }

  public View update(UUID id, Command raw, long version) {
    Command command = validate(raw);
    Long productInternalId = jdbc.query("""
        SELECT id FROM products WHERE public_id = UUID_TO_BIN(?)
        """, (rs, row) -> rs.getLong("id"), command.productId().toString())
        .stream().findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontramos el producto."));

    int changed = jdbc.update("""
        UPDATE quantity_promotions
        SET product_id = ?, name = ?, bundle_quantity = ?, bundle_price = ?,
            active = ?, starts_at = ?, ends_at = ?, version = version + 1
        WHERE public_id = UUID_TO_BIN(?) AND version = ?
        """,
        productInternalId,
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
    if (raw == null || raw.productId() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí un producto.");
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
    return new Command(raw.productId(), name, raw.bundleQuantity(), price, raw.active(), raw.startsAt(), raw.endsAt());
  }

  private static Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  public record Command(
      UUID productId,
      String name,
      int bundleQuantity,
      BigDecimal bundlePrice,
      boolean active,
      Instant startsAt,
      Instant endsAt) {}

  public record View(
      UUID id,
      UUID productId,
      String productName,
      String name,
      int bundleQuantity,
      BigDecimal bundlePrice,
      boolean active,
      Instant startsAt,
      Instant endsAt,
      long version) {}
}
