package com.comercioflex.shipping;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ShippingMigrationTests {
  @Container static final MySQLContainer<?> DATABASE = new MySQLContainer<>("mysql:8.4.10");

  @Test
  void preservesHistoricalOrderAndPaymentAmountsAndMigratesPickup() {
    var source =
        new DriverManagerDataSource(
            DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
    Flyway.configure()
        .dataSource(source)
        .locations("classpath:db/migration/tenant")
        .target("31")
        .load()
        .migrate();
    var jdbc = new JdbcTemplate(source);
    jdbc.update(
        "INSERT INTO store_settings(store_name,pickup_address,pickup_instructions)"
            + " VALUES('Tienda','Calle 123','De 9 a 18')");
    jdbc.update(
        """
INSERT INTO orders(public_id,idempotency_key,request_fingerprint,lookup_token_hash,status,fulfillment_type,
 customer_name,customer_phone,currency_code,subtotal,list_subtotal,discount_percentage,discount_amount,payment_method,reservation_expires_at)
VALUES(UUID_TO_BIN(UUID()),UUID_TO_BIN(UUID()),UNHEX(SHA2('request',256)),UNHEX(SHA2('token',256)),
 'PENDING_CONFIRMATION','PICKUP','Ana','123','ARS',4500,5000,10,500,'BANK_TRANSFER',UTC_TIMESTAMP(6)+INTERVAL 30 MINUTE)
""");
    var before =
        jdbc.queryForMap(
            "SELECT public_id,status,subtotal,list_subtotal,discount_amount,created_at FROM"
                + " orders");
    jdbc.update(
        "INSERT INTO bank_transfer_payments(public_id,order_id,attempt_number,status)"
            + " VALUES(UUID_TO_BIN(UUID()),1,1,'AWAITING_RECEIPT')");
    var paymentBefore = jdbc.queryForMap("SELECT * FROM bank_transfer_payments");
    var flyway =
        Flyway.configure().dataSource(source).locations("classpath:db/migration/tenant").load();
    flyway.migrate();
    flyway.validate();
    assertThat(
            jdbc.queryForMap(
                "SELECT public_id,status,subtotal,list_subtotal,discount_amount,created_at FROM"
                    + " orders"))
        .usingRecursiveComparison()
        .isEqualTo(before);
    assertThat(jdbc.queryForMap("SELECT * FROM bank_transfer_payments"))
        .usingRecursiveComparison()
        .isEqualTo(paymentBefore);
    assertThat(jdbc.queryForObject("SELECT total FROM orders", BigDecimal.class))
        .isEqualByComparingTo("4500");
    assertThat(jdbc.queryForObject("SELECT shipping_amount FROM orders", BigDecimal.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT shipping_snapshot FROM orders", String.class)).isNull();
    assertThat(jdbc.queryForObject("SELECT pickup_address FROM shipping_methods", String.class))
        .isEqualTo("Calle 123");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipments", Integer.class)).isZero();
  }
}
