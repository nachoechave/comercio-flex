package com.comercioflex;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ProductImageMigrationTests {

    @Container
    static final MySQLContainer<?> DATABASE = new MySQLContainer<>("mysql:8.4.10");

    @Test
    void preservesLegacyImageIdentityStorageAndProductData() {
        var source = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration/tenant").target("29").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO categories (public_id, name, slug, status) VALUES (UUID_TO_BIN(UUID()), 'Original', 'original', 'ACTIVE')");
        jdbc.update("INSERT INTO products (public_id, category_id, name, slug, status) VALUES (UUID_TO_BIN(UUID()), 1, 'Original', 'original', 'DRAFT')");
        jdbc.update("""
            INSERT INTO product_images (public_id, product_id, display_storage_key, thumbnail_storage_key,
                content_type, display_byte_size, thumbnail_byte_size, width, height, alt_text, sha256)
            VALUES (UUID_TO_BIN(UUID()), 1, 'tenant/products/display.png', 'tenant/products/thumbnail.png',
                'image/png', 10, 10, 1, 1, 'Original', REPEAT('a', 64))
            """);
        var before = jdbc.queryForMap("SELECT BIN_TO_UUID(public_id) public_id, display_storage_key, thumbnail_storage_key, created_at FROM product_images");
        var productBefore = jdbc.queryForMap("SELECT * FROM products");
        Flyway.configure().dataSource(source).locations("classpath:db/migration/tenant").load().migrate();
        assertThat(jdbc.queryForMap("SELECT BIN_TO_UUID(public_id) public_id, display_storage_key, thumbnail_storage_key, created_at FROM product_images"))
            .isEqualTo(before);
        assertThat(jdbc.queryForMap("SELECT * FROM products")).usingRecursiveComparison().isEqualTo(productBefore);
        assertThat(jdbc.queryForObject("SELECT position FROM product_images", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT is_primary FROM product_images", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_images", Integer.class)).isEqualTo(1);
    }
}
