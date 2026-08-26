package com.comercioflex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import com.comercioflex.tenant.application.TenantContext;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PublicCatalogIntegrationTests {

	private static final DockerImageName MYSQL_IMAGE =
		DockerImageName.parse("mysql:8.4.10");

	private static final String CATEGORY_A =
		"11111111-1111-4111-8111-111111111111";
	private static final String EMPTY_CATEGORY_A =
		"11111111-1111-4111-8111-111111111112";
	private static final String INACTIVE_CATEGORY_A =
		"11111111-1111-4111-8111-111111111113";
	private static final String CATEGORY_B =
		"22222222-2222-4222-8222-222222222222";
	private static final String PRODUCT_AVAILABLE =
		"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1";
	private static final String PRODUCT_EXHAUSTED =
		"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2";
	private static final String PRODUCT_B =
		"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1";
	private static final String VARIANT_AVAILABLE =
		"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0101";
	private static final String VARIANT_INACTIVE =
		"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0102";
	private static final String VARIANT_EXHAUSTED =
		"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0201";
	private static final String IMAGE_AVAILABLE =
		"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa1001";
	private static final String IMAGE_B =
		"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb1001";

	@Container
	static final MySQLContainer<?> CONTROL_DATABASE = new MySQLContainer<>(MYSQL_IMAGE);
	@Container
	static final MySQLContainer<?> TENANT_A_DATABASE = new MySQLContainer<>(MYSQL_IMAGE);
	@Container
	static final MySQLContainer<?> TENANT_B_DATABASE = new MySQLContainer<>(MYSQL_IMAGE);

	static {
		Startables.deepStart(Stream.of(
			CONTROL_DATABASE,
			TENANT_A_DATABASE,
			TENANT_B_DATABASE)).join();
	}

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private TenantContext tenantContext;

	@DynamicPropertySource
	static void configureDatabases(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", CONTROL_DATABASE::getJdbcUrl);
		registry.add("spring.datasource.username", CONTROL_DATABASE::getUsername);
		registry.add("spring.datasource.password", CONTROL_DATABASE::getPassword);
		registry.add("spring.flyway.user", CONTROL_DATABASE::getUsername);
		registry.add("spring.flyway.password", CONTROL_DATABASE::getPassword);
		registry.add("app.database.tenant-migration-enabled", () -> "true");
		registry.add(
			"app.database.migration-username",
			TENANT_A_DATABASE::getUsername);
		registry.add(
			"app.database.migration-password",
			TENANT_A_DATABASE::getPassword);
		registerTenant(registry, "tenant-a", TENANT_A_DATABASE);
		registerTenant(registry, "tenant-b", TENANT_B_DATABASE);
	}

	@BeforeEach
	void seed() throws SQLException {
		execute(CONTROL_DATABASE, "DELETE FROM memberships");
		execute(CONTROL_DATABASE, "DELETE FROM platform_users");
		execute(CONTROL_DATABASE, "DELETE FROM tenants");
		execute(CONTROL_DATABASE, """
			INSERT INTO tenants (public_id, slug, display_name, status, database_key)
			VALUES
				(UUID_TO_BIN(UUID()), 'tienda-a', 'Tienda A', 'ACTIVE', 'tenant-a'),
				(UUID_TO_BIN(UUID()), 'tienda-b', 'Tienda B', 'ACTIVE', 'tenant-b'),
				(UUID_TO_BIN(UUID()), 'tienda-inactiva', 'Inactiva', 'INACTIVE', 'tenant-inactive'),
				(UUID_TO_BIN(UUID()), 'tienda-sin-conexion', 'Sin conexiÃ³n', 'ACTIVE', 'tenant-unconfigured')
			""");

		resetTenant(TENANT_A_DATABASE);
		resetTenant(TENANT_B_DATABASE);
		insertCategory(TENANT_A_DATABASE, CATEGORY_A, "Moda", "moda", "ACTIVE");
		insertCategory(
			TENANT_A_DATABASE,
			EMPTY_CATEGORY_A,
			"Vacía",
			"vacia",
			"ACTIVE");
		insertCategory(
			TENANT_A_DATABASE,
			INACTIVE_CATEGORY_A,
			"Oculta",
			"oculta",
			"INACTIVE");
		insertCategory(TENANT_B_DATABASE, CATEGORY_B, "Moda B", "moda", "ACTIVE");

		insertProduct(
			TENANT_A_DATABASE,
			PRODUCT_AVAILABLE,
			CATEGORY_A,
			"Alfa %_ Especial",
			"producto-compartido",
			"Prenda liviana",
			"PUBLISHED");
		insertVariant(
			TENANT_A_DATABASE,
			VARIANT_AVAILABLE,
			PRODUCT_AVAILABLE,
			"SKU-SECRETO-A",
			"15900.00",
			"M",
			"Azul",
			"ACTIVE");
		insertVariant(
			TENANT_A_DATABASE,
			VARIANT_INACTIVE,
			PRODUCT_AVAILABLE,
			"SKU-INACTIVO",
			"9900.00",
			"L",
			"Rojo",
			"INACTIVE");
		insertBalance(TENANT_A_DATABASE, VARIANT_AVAILABLE, "5.000");
		insertProductImage(
			TENANT_A_DATABASE,
			IMAGE_AVAILABLE,
			PRODUCT_AVAILABLE,
			"Producto disponible de Moda");

		insertProduct(
			TENANT_A_DATABASE,
			PRODUCT_EXHAUSTED,
			CATEGORY_A,
			"Beta agotado",
			"beta-agotado",
			"Sin existencia",
			"PUBLISHED");
		insertVariant(
			TENANT_A_DATABASE,
			VARIANT_EXHAUSTED,
			PRODUCT_EXHAUSTED,
			"SKU-BETA",
			"20000.00",
			"",
			"",
			"ACTIVE");
		insertHiddenProducts();

		insertProduct(
			TENANT_B_DATABASE,
			PRODUCT_B,
			CATEGORY_B,
			"Producto B",
			"producto-compartido",
			"Pertenece a B",
			"PUBLISHED");
		insertVariant(
			TENANT_B_DATABASE,
			"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0101",
			PRODUCT_B,
			"SKU-SECRETO-B",
			"32000.00",
			"U",
			"Negro",
			"ACTIVE");
		insertProductImage(
			TENANT_B_DATABASE,
			IMAGE_B,
			PRODUCT_B,
			"Producto exclusivo de Moda B");
	}

	@Test
	void exposesAnonymousAlphabeticalCatalogWithDefaultPageAndNoStoreCache()
			throws Exception {
		mockMvc.perform(get(products("tienda-a")))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(24))
			.andExpect(jsonPath("$.totalItems").value(2))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.items[0].name").value("Alfa %_ Especial"))
			.andExpect(jsonPath("$.items[1].name").value("Beta agotado"))
			.andExpect(jsonPath("$.items[0].available").value(true))
			.andExpect(jsonPath("$.items[1].available").value(false))
			.andExpect(jsonPath("$.items[0].priceFrom").value("15900.00"))
			.andExpect(jsonPath("$.items[0].priceTo").value("15900.00"))
			.andExpect(jsonPath("$.items[0].sku").doesNotExist())
			.andExpect(jsonPath("$.items[0].quantity").doesNotExist())
			.andExpect(jsonPath("$.items[0].version").doesNotExist())
			.andExpect(jsonPath("$.items[0].status").doesNotExist());

		assertThat(tenantContext.currentDatabaseKey()).isEmpty();
	}

	@Test
	void listsOnlyNonEmptyVisibleCategories() throws Exception {
		mockMvc.perform(get(catalog("tienda-a") + "/categories"))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(CATEGORY_A))
			.andExpect(jsonPath("$[0].name").value("Moda"))
			.andExpect(jsonPath("$[0].slug").value("moda"))
			.andExpect(jsonPath("$[0].image.id").value(IMAGE_AVAILABLE))
			.andExpect(jsonPath("$[0].image.thumbnailUrl").value(
				"/api/v1/stores/tienda-a/media/product-images/"
					+ IMAGE_AVAILABLE + "/thumbnail"));
	}

	@Test
	void mapsEachVisibleCategoryToItsOwnRepresentativeImage() throws Exception {
		String hoodiesCategory = "11111111-1111-4111-8111-111111111121";
		String jacketsCategory = "11111111-1111-4111-8111-111111111122";
		String hoodiesProduct = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa21";
		String jacketsProduct = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa22";
		String hoodiesImage = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa1021";
		String jacketsImage = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa1022";
		insertCategory(TENANT_A_DATABASE, hoodiesCategory, "Buzos", "buzos", "ACTIVE");
		insertCategory(TENANT_A_DATABASE, jacketsCategory, "Camperas", "camperas", "ACTIVE");
		insertProduct(
			TENANT_A_DATABASE,
			hoodiesProduct,
			hoodiesCategory,
			"Buzo clásico",
			"buzo-clasico",
			null,
			"PUBLISHED");
		insertProduct(
			TENANT_A_DATABASE,
			jacketsProduct,
			jacketsCategory,
			"Campera urbana",
			"campera-urbana",
			null,
			"PUBLISHED");
		insertVariant(
			TENANT_A_DATABASE,
			"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0121",
			hoodiesProduct,
			"BUZO-1",
			"100.00",
			"",
			"",
			"ACTIVE");
		insertVariant(
			TENANT_A_DATABASE,
			"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0122",
			jacketsProduct,
			"CAMPERA-1",
			"200.00",
			"",
			"",
			"ACTIVE");
		insertProductImage(TENANT_A_DATABASE, hoodiesImage, hoodiesProduct, "Buzo negro");
		insertProductImage(TENANT_A_DATABASE, jacketsImage, jacketsProduct, "Campera azul");

		mockMvc.perform(get(catalog("tienda-a") + "/categories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[0].slug").value("buzos"))
			.andExpect(jsonPath("$[0].image.id").value(hoodiesImage))
			.andExpect(jsonPath("$[1].slug").value("camperas"))
			.andExpect(jsonPath("$[1].image.id").value(jacketsImage))
			.andExpect(jsonPath("$[2].slug").value("moda"))
			.andExpect(jsonPath("$[2].image.id").value(IMAGE_AVAILABLE));

		mockMvc.perform(get(catalog("tienda-b") + "/categories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].image.id").value(IMAGE_B));
	}

	@Test
	void detailUsesSlugHidesInactiveVariantsAndOperationalFields() throws Exception {
		mockMvc.perform(get(products("tienda-a") + "/producto-compartido"))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(jsonPath("$.id").value(PRODUCT_AVAILABLE))
			.andExpect(jsonPath("$.description").value("Prenda liviana"))
			.andExpect(jsonPath("$.variants.length()").value(1))
			.andExpect(jsonPath("$.variants[0].id").value(VARIANT_AVAILABLE))
			.andExpect(jsonPath("$.variants[0].price").value("15900.00"))
			.andExpect(jsonPath("$.variants[0].size").value("M"))
			.andExpect(jsonPath("$.variants[0].color").value("Azul"))
			.andExpect(jsonPath("$.variants[0].available").value(true))
			.andExpect(jsonPath("$.variants[0].sku").doesNotExist())
			.andExpect(jsonPath("$.variants[0].quantity").doesNotExist())
			.andExpect(jsonPath("$.variants[0].version").doesNotExist());

		mockMvc.perform(get(products("tienda-a") + "/beta-agotado"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.variants[0].available").value(false))
			.andExpect(jsonPath("$.variants[0].size").doesNotExist())
			.andExpect(jsonPath("$.variants[0].color").doesNotExist());
	}

	@Test
	void isolatesTenantsEvenWhenProductSlugsAreEqual() throws Exception {
		mockMvc.perform(get(products("tienda-a") + "/producto-compartido"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Alfa %_ Especial"))
			.andExpect(jsonPath("$.variants[0].price").value("15900.00"));
		assertThat(tenantContext.currentDatabaseKey()).isEmpty();

		mockMvc.perform(get(products("tienda-b") + "/producto-compartido"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Producto B"))
			.andExpect(jsonPath("$.variants[0].price").value("32000.00"))
			.andExpect(jsonPath("$.variants[0].available").value(false));
		assertThat(tenantContext.currentDatabaseKey()).isEmpty();
	}

	@Test
	void supportsLiteralSearchCategoryFilterAndPagination() throws Exception {
		mockMvc.perform(get(products("tienda-a"))
				.param("q", "%_")
				.param("category", "moda"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].id").value(PRODUCT_AVAILABLE));

		mockMvc.perform(get(products("tienda-a"))
				.param("q", "  prenda   liviana  "))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(0))
			.andExpect(jsonPath("$.items").isEmpty());

		mockMvc.perform(get(products("tienda-a"))
				.param("category", "no-existe"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.totalItems").value(0));

		mockMvc.perform(get(products("tienda-a"))
				.param("page", "1")
				.param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].name").value("Beta agotado"))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.size").value(1))
			.andExpect(jsonPath("$.totalPages").value(2));

		mockMvc.perform(get(products("tienda-a"))
				.param("page", "10000"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.page").value(10000))
			.andExpect(jsonPath("$.totalItems").value(2));
	}

	@Test
	void hidesDraftArchivedInactiveCategoryAndProductsWithoutActiveVariants()
			throws Exception {
		for (String slug : Stream.of(
				"borrador",
				"archivado",
				"categoria-inactiva",
				"sin-variante-activa").toList()) {
			mockMvc.perform(get(products("tienda-a") + "/" + slug))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.type")
					.value("https://comercio-flex.local/problems/product-not-found"));
		}
	}

	@Test
	void rejectsInvalidLimitsAndDoesNotOpenMutatingCatalogRoutes() throws Exception {
		mockMvc.perform(get(products("tienda-a")).param("size", "61"))
			.andExpect(status().isBadRequest());
		mockMvc.perform(get(products("tienda-a")).param("page", "-1"))
			.andExpect(status().isBadRequest());
		mockMvc.perform(get(products("tienda-a")).param("q", "a".repeat(101)))
			.andExpect(status().isBadRequest());
		mockMvc.perform(get(products("tienda-a")).param("category", "../otra"))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post(products("tienda-a")))
			.andExpect(status().isForbidden());
		mockMvc.perform(put(products("tienda-a")))
			.andExpect(status().isForbidden());
		mockMvc.perform(patch(products("tienda-a")))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete(products("tienda-a")))
			.andExpect(status().isForbidden());
	}

	@Test
	void returnsGenericNotFoundForMissingInactiveAndUnconfiguredStores()
			throws Exception {
		mockMvc.perform(get(products("no-existe")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.title").value("Tienda no encontrada"));
		mockMvc.perform(get(products("tienda-inactiva")))
			.andExpect(status().isNotFound())
			.andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"))
			.andExpect(jsonPath("$.title").value("Tienda no encontrada"));
		mockMvc.perform(get(products("tienda-sin-conexion")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.title").value("Tienda no encontrada"));
		mockMvc.perform(get(products("tienda-a"))
				.header("X-Database-Key", "tenant-b"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].name").value("Alfa %_ Especial"));
		assertThat(tenantContext.currentDatabaseKey()).isEmpty();
	}

	private void insertHiddenProducts() throws SQLException {
		insertProduct(
			TENANT_A_DATABASE,
			"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3",
			CATEGORY_A,
			"Borrador",
			"borrador",
			null,
			"DRAFT");
		insertVariant(
			TENANT_A_DATABASE,
			"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0301",
			"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3",
			"SKU-DRAFT",
			"100.00",
			"",
			"",
			"ACTIVE");
		insertProduct(
			TENANT_A_DATABASE,
			"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa4",
			CATEGORY_A,
			"Archivado",
			"archivado",
			null,
			"ARCHIVED");
		insertVariant(
			TENANT_A_DATABASE,
			"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0401",
			"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa4",
			"SKU-ARCH",
			"100.00",
			"",
			"",
			"ACTIVE");
		insertProduct(
			TENANT_A_DATABASE,
			"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa5",
			INACTIVE_CATEGORY_A,
			"Categoría inactiva",
			"categoria-inactiva",
			null,
			"PUBLISHED");
		insertVariant(
			TENANT_A_DATABASE,
			"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0501",
			"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa5",
			"SKU-CAT-INACTIVE",
			"100.00",
			"",
			"",
			"ACTIVE");
		insertProduct(
			TENANT_A_DATABASE,
			"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa6",
			CATEGORY_A,
			"Sin variante activa",
			"sin-variante-activa",
			null,
			"PUBLISHED");
		insertVariant(
			TENANT_A_DATABASE,
			"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0601",
			"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa6",
			"SKU-NO-ACTIVE",
			"100.00",
			"",
			"",
			"INACTIVE");
	}

	private static void resetTenant(MySQLContainer<?> database) throws SQLException {
		execute(database, "DELETE FROM inventory_movements");
		execute(database, "DELETE FROM inventory_balances");
		execute(database, "DELETE FROM product_images");
		execute(database, "DELETE FROM product_variant_option_values");
		execute(database, "DELETE FROM product_option_values");
		execute(database, "DELETE FROM product_options");
		execute(database, "DELETE FROM product_variants");
		execute(database, "DELETE FROM products");
		execute(database, "DELETE FROM categories");
	}

	private static void insertCategory(
			MySQLContainer<?> database,
			String id,
			String name,
			String slug,
			String status) throws SQLException {
		execute(database, """
			INSERT INTO categories (public_id, name, slug, status)
			VALUES (UUID_TO_BIN('%s'), '%s', '%s', '%s')
			""".formatted(id, name, slug, status));
	}

	private static void insertProduct(
			MySQLContainer<?> database,
			String id,
			String categoryId,
			String name,
			String slug,
			String description,
			String status) throws SQLException {
		String descriptionSql = description == null
			? "NULL"
			: "'" + description.replace("'", "''") + "'";
		execute(database, """
			INSERT INTO products (
				public_id, category_id, name, slug, description, status
			)
			SELECT
				UUID_TO_BIN('%s'), category.id, '%s', '%s', %s, '%s'
			FROM categories category
			WHERE category.public_id = UUID_TO_BIN('%s')
			""".formatted(
				id,
				name.replace("'", "''"),
				slug,
				descriptionSql,
				status,
				categoryId));
	}

	private static void insertVariant(
			MySQLContainer<?> database,
			String id,
			String productId,
			String sku,
			String price,
			String size,
			String color,
			String status) throws SQLException {
		execute(database, """
			INSERT INTO product_variants (
				public_id, product_id, sku, price, size_value, color_value,
				option_signature, status
			)
			SELECT
				UUID_TO_BIN('%s'), product.id, '%s', %s, '%s', '%s', SHA2(UUID(), 256), '%s'
			FROM products product
			WHERE product.public_id = UUID_TO_BIN('%s')
			""".formatted(id, sku, price, size, color, status, productId));
	}

	private static void insertBalance(
			MySQLContainer<?> database,
			String variantId,
			String quantity) throws SQLException {
		execute(database, """
			INSERT INTO inventory_balances (variant_id, quantity)
			SELECT variant.id, %s
			FROM product_variants variant
			WHERE variant.public_id = UUID_TO_BIN('%s')
			""".formatted(quantity, variantId));
	}

	private static void insertProductImage(
			MySQLContainer<?> database,
			String id,
			String productId,
			String altText) throws SQLException {
		execute(database, """
			INSERT INTO product_images (
				public_id, product_id, display_storage_key, thumbnail_storage_key,
				content_type, display_byte_size, thumbnail_byte_size, width, height,
				alt_text, sha256
			)
			SELECT
				UUID_TO_BIN('%s'), product.id, '%s/display.png', '%s/thumbnail.png',
				'image/png', 100, 50, 1200, 900, '%s', SHA2('%s', 256)
			FROM products product
			WHERE product.public_id = UUID_TO_BIN('%s')
			""".formatted(
				id,
				id,
				id,
				altText.replace("'", "''"),
				id,
				productId));
	}

	private static void registerTenant(
			DynamicPropertyRegistry registry,
			String databaseKey,
			MySQLContainer<?> database) {
		String prefix = "app.database.tenant-connections." + databaseKey;
		registry.add(prefix + ".url", database::getJdbcUrl);
		registry.add(prefix + ".username", database::getUsername);
		registry.add(prefix + ".password", database::getPassword);
	}

	private static void execute(MySQLContainer<?> database, String sql)
			throws SQLException {
		try (
			Connection connection = DriverManager.getConnection(
				database.getJdbcUrl(),
				database.getUsername(),
				database.getPassword());
			Statement statement = connection.createStatement()
		) {
			statement.execute(sql);
		}
	}

	private String catalog(String storeSlug) {
		return "/api/v1/stores/" + storeSlug + "/catalog";
	}

	private String products(String storeSlug) {
		return catalog(storeSlug) + "/products";
	}
}
