package com.comercioflex.platformadmin.application;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformBillingService {

  private final JdbcTemplate jdbc;

  public PlatformBillingService(@Qualifier("controlJdbcTemplate") JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public BillingMonth month(int year, int month) {
    validatePeriod(year, month);
    LocalDate current = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
    LocalDate period = LocalDate.of(year, month, 1);
    List<BillingRow> rows = jdbc.query("""
        SELECT BIN_TO_UUID(t.public_id) company_id,
               t.display_name company_name,
               t.slug,
               t.status company_status,
               p.amount,
               p.status payment_status,
               p.payment_method,
               p.paid_at,
               p.notes
        FROM tenants t
        LEFT JOIN platform_monthly_payments p
          ON p.tenant_id = t.id
         AND p.period_year = ?
         AND p.period_month = ?
        WHERE t.tenant_type = 'ECOMMERCE'
          AND t.status IN ('ACTIVE','INACTIVE','SUSPENDED')
        ORDER BY t.display_name
        """, (rs, n) -> {
          String stored = rs.getString("payment_status");
          String status = stored != null
              ? stored
              : (period.isBefore(current) ? "OVERDUE" : "PENDING");
          return new BillingRow(
              UUID.fromString(rs.getString("company_id")),
              rs.getString("company_name"),
              rs.getString("slug"),
              rs.getString("company_status"),
              status,
              rs.getBigDecimal("amount"),
              rs.getString("payment_method"),
              instant(rs.getTimestamp("paid_at")),
              rs.getString("notes"));
        }, year, month);

    long paid = rows.stream().filter(r -> "PAID".equals(r.status())).count();
    long bonified = rows.stream().filter(r -> "BONIFIED".equals(r.status())).count();
    long pending = rows.stream().filter(r -> "PENDING".equals(r.status())).count();
    long overdue = rows.stream().filter(r -> "OVERDUE".equals(r.status())).count();
    BigDecimal collected = rows.stream()
        .filter(r -> "PAID".equals(r.status()) && r.amount() != null)
        .map(BillingRow::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    return new BillingMonth(year, month, rows.size(), paid, bonified, pending, overdue, collected, rows);
  }

  public BillingRow save(UUID companyId, SavePayment raw) {
    if (raw == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Completá el pago.");
    validatePeriod(raw.year(), raw.month());
    String status = raw.status() == null ? "" : raw.status().trim().toUpperCase();
    if (!List.of("PENDING", "PAID", "BONIFIED").contains(status)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El estado del pago no es válido.");
    }
    if ("PAID".equals(status) && (raw.amount() == null || raw.amount().signum() < 0)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indicá un monto válido.");
    }
    Long tenantId = jdbc.query("""
        SELECT id FROM tenants
        WHERE public_id = UUID_TO_BIN(?) AND tenant_type = 'ECOMMERCE'
        """, (rs, n) -> rs.getLong(1), companyId.toString()).stream().findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontramos la empresa."));

    Instant paidAt = "PAID".equals(status)
        ? (raw.paidAt() == null ? Instant.now() : raw.paidAt())
        : null;
    String method = text(raw.paymentMethod(), 40);
    String notes = text(raw.notes(), 500);

    jdbc.update("""
        INSERT INTO platform_monthly_payments
          (public_id, tenant_id, period_year, period_month, amount, status, payment_method, paid_at, notes)
        VALUES (UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          amount = VALUES(amount),
          status = VALUES(status),
          payment_method = VALUES(payment_method),
          paid_at = VALUES(paid_at),
          notes = VALUES(notes)
        """,
        UUID.randomUUID().toString(), tenantId, raw.year(), raw.month(),
        raw.amount(), status, method, timestamp(paidAt), notes);

    return month(raw.year(), raw.month()).rows().stream()
        .filter(row -> row.companyId().equals(companyId))
        .findFirst()
        .orElseThrow();
  }

  private void validatePeriod(int year, int month) {
    if (year < 2020 || year > 2100 || month < 1 || month > 12) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El período no es válido.");
    }
  }

  private static String text(String value, int max) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim();
    if (normalized.length() > max) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El texto supera el máximo permitido.");
    }
    return normalized;
  }

  private static Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  public record SavePayment(
      int year,
      int month,
      BigDecimal amount,
      String status,
      String paymentMethod,
      Instant paidAt,
      String notes) {}

  public record BillingRow(
      UUID companyId,
      String companyName,
      String slug,
      String companyStatus,
      String status,
      BigDecimal amount,
      String paymentMethod,
      Instant paidAt,
      String notes) {}

  public record BillingMonth(
      int year,
      int month,
      long total,
      long paid,
      long bonified,
      long pending,
      long overdue,
      BigDecimal collected,
      List<BillingRow> rows) {}
}
