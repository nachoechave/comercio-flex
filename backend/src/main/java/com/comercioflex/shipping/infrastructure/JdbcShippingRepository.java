package com.comercioflex.shipping.infrastructure;

import com.comercioflex.shipping.application.*;
import com.comercioflex.shipping.domain.ShippingModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcShippingRepository implements ShippingRepository {
  @Override
  public void cancelPending(UUID orderId) {
    jdbc.update(
        """
        UPDATE shipments s JOIN orders o ON o.id=s.order_id
        SET s.status='CANCELLED',s.version=s.version+1
        WHERE o.public_id=UUID_TO_BIN(?) AND s.status IN ('PENDING','PREPARING')
        """,
        orderId.toString());
  }

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public JdbcShippingRepository(
      @Qualifier("tenantJdbcTemplate") JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public Settings settings(boolean lock) {
    return jdbc.query(
            "SELECT free_shipping_threshold,version FROM shipping_settings WHERE id=1"
                + (lock ? " FOR UPDATE" : ""),
            (rs, n) ->
                new Settings(
                    rs.getBigDecimal(1),
                    rs.getLong(2),
                    jdbc.query(
                        "SELECT * FROM shipping_methods ORDER BY name,id",
                        (m, i) ->
                            new Method(
                                UUID.fromString(m.getString("id")),
                                m.getString("name"),
                                m.getString("description"),
                                MethodType.valueOf(m.getString("type")),
                                m.getBigDecimal("price"),
                                m.getBoolean("active"),
                                m.getString("pickup_address"),
                                m.getString("instructions"),
                                jdbc.query(
                                    "SELECT destination,price FROM shipping_rules WHERE method_id=?"
                                        + " ORDER BY destination",
                                    (r, k) -> new Rule(r.getString(1), r.getBigDecimal(2)),
                                    m.getString("id"))))))
        .getFirst();
  }

  public void save(Settings s) {
    jdbc.update("DELETE FROM shipping_rules");
    jdbc.update("DELETE FROM shipping_methods");
    for (Method m : s.methods()) {
      jdbc.update(
          "INSERT INTO shipping_methods VALUES (?,?,?,?,?,?,?,?)",
          m.id().toString(),
          m.name(),
          m.description(),
          m.type().name(),
          m.price(),
          m.active(),
          m.pickupAddress(),
          m.instructions());
      for (Rule r : m.rules())
        jdbc.update(
            "INSERT INTO shipping_rules VALUES (?,?,?)",
            m.id().toString(),
            r.destination(),
            r.price());
    }
    jdbc.update(
        "UPDATE shipping_settings SET free_shipping_threshold=?,version=version+1 WHERE id=1",
        s.freeShippingThreshold());
  }

  public void attach(long orderId, Snapshot snapshot) {
    try {
      jdbc.update(
          "UPDATE orders SET fulfillment_type=?,shipping_amount=?,shipping_snapshot=? WHERE id=?",
          snapshot.type() == MethodType.PICKUP ? "PICKUP" : "SHIPPING",
          snapshot.cost(),
          json.writeValueAsString(snapshot),
          orderId);
      if (snapshot.type() != MethodType.PICKUP)
        jdbc.update(
            "INSERT INTO shipments(id,order_id,shipping_cost) VALUES (?,?,?)",
            UUID.randomUUID().toString(),
            orderId,
            snapshot.cost());
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  public String lockOrderStatus(UUID orderId) {
    return jdbc
        .query(
            "SELECT status FROM orders WHERE public_id=UUID_TO_BIN(?) FOR UPDATE",
            (rs, n) -> rs.getString(1),
            orderId.toString())
        .stream()
        .findFirst()
        .orElseThrow(() -> new ShippingException("Pedido inexistente."));
  }

  public Shipment shipment(UUID orderId, boolean lock) {
    return jdbc
        .query(
            "SELECT s.* FROM shipments s JOIN orders o ON o.id=s.order_id WHERE"
                + " o.public_id=UUID_TO_BIN(?)"
                + (lock ? " FOR UPDATE" : ""),
            (r, n) ->
                new Shipment(
                    UUID.fromString(r.getString("id")),
                    orderId,
                    r.getString("provider"),
                    Status.valueOf(r.getString("status")),
                    r.getString("carrier_name"),
                    r.getString("tracking_number"),
                    r.getString("tracking_url"),
                    r.getBigDecimal("shipping_cost"),
                    instant(r.getTimestamp("created_at")),
                    instant(r.getTimestamp("shipped_at")),
                    instant(r.getTimestamp("delivered_at")),
                    r.getString("notes"),
                    r.getLong("version")),
            orderId.toString())
        .stream()
        .findFirst()
        .orElse(null);
  }

  public void update(UUID orderId, UpdateShipment u) {
    jdbc.update(
        """
        UPDATE shipments s JOIN orders o ON o.id=s.order_id
        SET s.status=?,s.carrier_name=?,s.tracking_number=?,s.tracking_url=?,s.notes=?,
         s.shipped_at=IF(?='SHIPPED',COALESCE(s.shipped_at,UTC_TIMESTAMP(6)),s.shipped_at),
         s.delivered_at=IF(?='DELIVERED',COALESCE(s.delivered_at,UTC_TIMESTAMP(6)),s.delivered_at),
         s.version=s.version+1 WHERE o.public_id=UUID_TO_BIN(?)
        """,
        u.status().name(),
        u.carrierName(),
        u.trackingNumber(),
        u.trackingUrl(),
        u.notes(),
        u.status().name(),
        u.status().name(),
        orderId.toString());
  }

  private Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
