package com.comercioflex.shipping.infrastructure;

import com.comercioflex.payment.application.EncryptedSecret;
import com.comercioflex.shipping.application.CarrierRepository;
import com.comercioflex.shipping.application.ShippingException;
import com.comercioflex.shipping.domain.CarrierModels.*;
import com.comercioflex.shipping.domain.ShippingModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCarrierRepository implements CarrierRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public JdbcCarrierRepository(
      @Qualifier("tenantJdbcTemplate") JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public StoredSettings settings(boolean lock) {
    return jdbc.query(
            "SELECT * FROM shipping_carrier_settings WHERE id=1" + (lock ? " FOR UPDATE" : ""),
            (rs, row) -> mapSettings(rs))
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Falta shipping_carrier_settings."));
  }

  @Override
  public void save(StoredSettings s) {
    jdbc.update(
        """
        UPDATE shipping_carrier_settings SET
          provider=?, enabled=?, environment=?, client_code=?, contract_code=?,
          username_key_id=?, username_nonce=?, username_ciphertext=?,
          password_key_id=?, password_nonce=?, password_ciphertext=?, credential_context=?,
          origin_postal_code=?, origin_street=?, origin_number=?, origin_city=?, origin_province=?, origin_country=?,
          sender_name=?, sender_email=?, sender_phone=?, sender_document_type=?, sender_document_number=?,
          default_weight_grams=?, default_length_cm=?, default_width_cm=?, default_height_cm=?,
          tracking_sync_minutes=?, version=version+1
        WHERE id=1
        """,
        s.provider().name(),
        s.enabled(),
        s.environment().name(),
        s.clientCode(),
        s.contractCode(),
        key(s.username()),
        nonce(s.username()),
        cipher(s.username()),
        key(s.password()),
        nonce(s.password()),
        cipher(s.password()),
        s.credentialContext(),
        s.origin() == null ? null : s.origin().postalCode(),
        s.origin() == null ? null : s.origin().street(),
        s.origin() == null ? null : s.origin().number(),
        s.origin() == null ? null : s.origin().city(),
        s.origin() == null ? null : s.origin().province(),
        s.origin() == null ? "Argentina" : s.origin().country(),
        s.origin() == null ? null : s.origin().senderName(),
        s.origin() == null ? null : s.origin().senderEmail(),
        s.origin() == null ? null : s.origin().senderPhone(),
        s.origin() == null ? null : s.origin().senderDocumentType().name(),
        s.origin() == null ? null : s.origin().senderDocumentNumber(),
        s.defaultParcel() == null ? null : s.defaultParcel().weightGrams(),
        s.defaultParcel() == null ? null : s.defaultParcel().lengthCm(),
        s.defaultParcel() == null ? null : s.defaultParcel().widthCm(),
        s.defaultParcel() == null ? null : s.defaultParcel().heightCm(),
        s.trackingSyncMinutes());
  }

  @Override
  public void saveQuote(StoredQuote q) {
    jdbc.update(
        "DELETE FROM shipping_carrier_quotes WHERE expires_at < UTC_TIMESTAMP(6) - INTERVAL 1 DAY");
    jdbc.update(
        """
        INSERT INTO shipping_carrier_quotes(
          id,method_id,provider,service_code,provider_cost,shipping_amount,list_subtotal,
          discount_amount,total,postal_code,weight_grams,length_cm,width_cm,height_cm,expires_at
        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        q.id().toString(),
        q.methodId().toString(),
        q.provider().name(),
        q.serviceCode(),
        q.providerCost(),
        q.shippingAmount(),
        q.listSubtotal(),
        q.discountAmount(),
        q.total(),
        q.postalCode(),
        q.parcel().weightGrams(),
        q.parcel().lengthCm(),
        q.parcel().widthCm(),
        q.parcel().heightCm(),
        Timestamp.from(q.expiresAt()));
  }

  @Override
  public Optional<StoredQuote> quote(UUID id, boolean lock) {
    return jdbc.query(
            "SELECT * FROM shipping_carrier_quotes WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            (rs, row) ->
                new StoredQuote(
                    UUID.fromString(rs.getString("id")),
                    UUID.fromString(rs.getString("method_id")),
                    Provider.valueOf(rs.getString("provider")),
                    rs.getString("service_code"),
                    rs.getBigDecimal("provider_cost"),
                    rs.getBigDecimal("shipping_amount"),
                    rs.getBigDecimal("list_subtotal"),
                    rs.getBigDecimal("discount_amount"),
                    rs.getBigDecimal("total"),
                    rs.getString("postal_code"),
                    new Parcel(
                        rs.getBigDecimal("weight_grams"),
                        rs.getBigDecimal("length_cm"),
                        rs.getBigDecimal("width_cm"),
                        rs.getBigDecimal("height_cm")),
                    instant(rs.getTimestamp("expires_at")),
                    instant(rs.getTimestamp("consumed_at"))),
            id.toString())
        .stream()
        .findFirst();
  }

  @Override
  public void consumeQuote(UUID id) {
    int changed =
        jdbc.update(
            "UPDATE shipping_carrier_quotes SET consumed_at=UTC_TIMESTAMP(6) WHERE id=? AND consumed_at IS NULL",
            id.toString());
    if (changed != 1) throw new ShippingException("La cotización de transporte ya fue utilizada.");
  }

  @Override
  public Optional<ProvisionOrder> provisionOrder(UUID orderId) {
    return jdbc.query(
            """
            SELECT o.status,o.customer_name,o.customer_email,o.customer_phone,o.list_subtotal,o.shipping_snapshot,
                   s.external_reference,s.label_reference
            FROM orders o
            JOIN shipments s ON s.order_id=o.id
            WHERE o.public_id=UUID_TO_BIN(?)
            """,
            (rs, row) ->
                new ProvisionOrder(
                    orderId,
                    rs.getString("status"),
                    rs.getString("customer_name"),
                    rs.getString("customer_email"),
                    rs.getString("customer_phone"),
                    rs.getBigDecimal("list_subtotal"),
                    snapshot(rs.getString("shipping_snapshot")),
                    rs.getString("external_reference"),
                    rs.getString("label_reference")),
            orderId.toString())
        .stream()
        .findFirst();
  }

  @Override
  public boolean claimProvision(UUID orderId, Provider provider) {
    return jdbc.update(
            """
            UPDATE shipments s JOIN orders o ON o.id=s.order_id
            SET s.provider=?,s.provider_status='PROVISIONING',s.provider_error=NULL,
                s.last_synced_at=UTC_TIMESTAMP(6),s.version=s.version+1
            WHERE o.public_id=UUID_TO_BIN(?)
              AND s.external_reference IS NULL
              AND (s.provider_status IS NULL OR s.provider_status<>'PROVISIONING')
            """,
            provider.name(),
            orderId.toString())
        == 1;
  }

  @Override
  public void saveProvisioned(
      UUID orderId,
      Provider provider,
      java.math.BigDecimal providerCost,
      String externalReference,
      String labelReference,
      String trackingNumber,
      String trackingUrl,
      String providerStatus) {
    int changed =
        jdbc.update(
            """
            UPDATE shipments s JOIN orders o ON o.id=s.order_id
            SET s.provider=?,s.provider_cost=?,s.external_reference=?,s.label_reference=?,
                s.carrier_name='Andreani',s.tracking_number=?,s.tracking_url=?,s.provider_status=?,
                s.status=CASE WHEN s.status='PENDING' THEN 'PREPARING' ELSE s.status END,
                s.last_synced_at=UTC_TIMESTAMP(6),s.provider_error=NULL,s.version=s.version+1
            WHERE o.public_id=UUID_TO_BIN(?) AND s.external_reference IS NULL
            """,
            provider.name(),
            providerCost,
            externalReference,
            labelReference,
            trackingNumber,
            trackingUrl,
            providerStatus,
            orderId.toString());
    if (changed != 1) {
      String existing =
          jdbc.query(
                  "SELECT s.external_reference FROM shipments s JOIN orders o ON o.id=s.order_id WHERE o.public_id=UUID_TO_BIN(?)",
                  (rs, row) -> rs.getString(1),
                  orderId.toString())
              .stream()
              .findFirst()
              .orElse(null);
      if (existing == null) throw new ShippingException("No pudimos guardar el envío externo.");
    }
  }

  @Override
  public void saveProviderError(UUID orderId, String message) {
    jdbc.update(
        """
        UPDATE shipments s JOIN orders o ON o.id=s.order_id
        SET s.provider_error=?,
            s.provider_status=CASE WHEN s.external_reference IS NULL THEN 'PROVISION_FAILED' ELSE s.provider_status END,
            s.last_synced_at=UTC_TIMESTAMP(6),s.version=s.version+1
        WHERE o.public_id=UUID_TO_BIN(?)
        """,
        truncate(message, 1000),
        orderId.toString());
  }

  @Override
  public void saveTracking(
      UUID orderId,
      String providerStatus,
      Status mappedStatus,
      Instant occurredAt,
      Instant syncedAt) {
    jdbc.update(
        """
        UPDATE shipments s JOIN orders o ON o.id=s.order_id
        SET s.provider_status=?,s.last_synced_at=?,s.provider_error=NULL,
            s.status=CASE
              WHEN ?='DELIVERED' THEN 'DELIVERED'
              WHEN ?='CANCELLED' AND s.status IN ('PENDING','PREPARING') THEN 'CANCELLED'
              WHEN ?='SHIPPED' AND s.status IN ('PENDING','PREPARING') THEN 'SHIPPED'
              WHEN ?='PREPARING' AND s.status='PENDING' THEN 'PREPARING'
              ELSE s.status END,
            s.shipped_at=CASE WHEN ?='SHIPPED' THEN COALESCE(s.shipped_at,?) ELSE s.shipped_at END,
            s.delivered_at=CASE WHEN ?='DELIVERED' THEN COALESCE(s.delivered_at,?) ELSE s.delivered_at END,
            s.version=s.version+1
        WHERE o.public_id=UUID_TO_BIN(?) AND s.status<>'CANCELLED'
        """,
        truncate(providerStatus, 160),
        Timestamp.from(syncedAt),
        mappedStatus.name(),
        mappedStatus.name(),
        mappedStatus.name(),
        mappedStatus.name(),
        mappedStatus.name(),
        Timestamp.from(occurredAt == null ? syncedAt : occurredAt),
        mappedStatus.name(),
        Timestamp.from(occurredAt == null ? syncedAt : occurredAt),
        orderId.toString());
  }

  private StoredSettings mapSettings(ResultSet rs) throws SQLException {
    Origin origin = null;
    if (rs.getString("origin_postal_code") != null) {
      origin =
          new Origin(
              rs.getString("origin_postal_code"),
              rs.getString("origin_street"),
              rs.getString("origin_number"),
              rs.getString("origin_city"),
              rs.getString("origin_province"),
              rs.getString("origin_country"),
              rs.getString("sender_name"),
              rs.getString("sender_email"),
              rs.getString("sender_phone"),
              rs.getString("sender_document_type") == null
                  ? null
                  : DocumentType.valueOf(rs.getString("sender_document_type")),
              rs.getString("sender_document_number"));
    }
    Parcel parcel = null;
    if (rs.getBigDecimal("default_weight_grams") != null) {
      parcel =
          new Parcel(
              rs.getBigDecimal("default_weight_grams"),
              rs.getBigDecimal("default_length_cm"),
              rs.getBigDecimal("default_width_cm"),
              rs.getBigDecimal("default_height_cm"));
    }
    return new StoredSettings(
        Provider.valueOf(rs.getString("provider")),
        rs.getBoolean("enabled"),
        Environment.valueOf(rs.getString("environment")),
        rs.getString("client_code"),
        rs.getString("contract_code"),
        secret(rs, "username"),
        secret(rs, "password"),
        rs.getString("credential_context"),
        origin,
        parcel,
        rs.getInt("tracking_sync_minutes"),
        rs.getLong("version"));
  }

  private EncryptedSecret secret(ResultSet rs, String prefix) throws SQLException {
    String key = rs.getString(prefix + "_key_id");
    byte[] nonce = rs.getBytes(prefix + "_nonce");
    byte[] ciphertext = rs.getBytes(prefix + "_ciphertext");
    return key == null || nonce == null || ciphertext == null
        ? null
        : new EncryptedSecret(key, nonce, ciphertext);
  }

  private Snapshot snapshot(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return json.readValue(raw, Snapshot.class);
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo leer el snapshot de envío.", e);
    }
  }

  private String key(EncryptedSecret secret) {
    return secret == null ? null : secret.keyId();
  }

  private byte[] nonce(EncryptedSecret secret) {
    return secret == null ? null : secret.nonce();
  }

  private byte[] cipher(EncryptedSecret secret) {
    return secret == null ? null : secret.ciphertext();
  }

  private Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  private String truncate(String value, int max) {
    if (value == null) return null;
    return value.length() <= max ? value : value.substring(0, max);
  }
}
