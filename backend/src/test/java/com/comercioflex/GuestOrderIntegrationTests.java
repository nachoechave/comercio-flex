package com.comercioflex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class GuestOrderIntegrationTests {

	private static final DockerImageName MYSQL_IMAGE =
		DockerImageName.parse("mysql:8.4.10");
	private static final String CATEGORY_A =
		"11111111-1111-4111-8111-111111111111";
	private static final String PRODUCT_A =
		"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1";
	private static final String VARIANT_A =
		"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0101";

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
	private ObjectMapper objectMapper;

	@DynamicPropertySource
	static void configureDatabases(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", CONTROL_DATABASE::getJdbcUrl);
		registry.add("spring.datasource.username", CONTROL_DATABASE::getUsername);
		registry.add("spring.datasource.password", CONTROL_DATABASE::getPassword);
		registry.add("spring.flyway.user", CONTROL_DATABASE::getUsername);
		registry.add("spring.flyway.password", CONTROL_DATABASE::getPassword);
		registry.add("app.database.tenant-migration-enabled", () -> "true");
		registry.add("app.database.migration-username", TENANT_A_DATABASE::getUsername);
		registry.add("app.database.migration-password", TENANT_A_DATABASE::getPassword);
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
				(UUID_TO_BIN(UUID()), 'tienda-b', 'Tienda B', 'ACTIVE', 'tenant-b')
			""");
		resetTenant(TENANT_A_DATABASE, "Tienda A");
		resetTenant(TENANT_B_DATABASE, "Tienda B");
		execute(TENANT_A_DATABASE, """
			INSERT INTO categories (public_id, name, slug, status)
			VALUES (UUID_TO_BIN('%s'), 'Carnes', 'carnes', 'ACTIVE')
			""".formatted(CATEGORY_A));
		execute(TENANT_A_DATABASE, """
			INSERT INTO products (public_id, category_id, name, slug, status)
			SELECT UUID_TO_BIN('%s'), id, 'Asado', 'asado', 'PUBLISHED'
			FROM categories WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(PRODUCT_A, CATEGORY_A));
		execute(TENANT_A_DATABASE, """
			INSERT INTO product_variants (
				public_id, product_id, sku, price, size_value, color_value, status
			)
			SELECT UUID_TO_BIN('%s'), id, 'ASA-001', 2500.00, '', '', 'ACTIVE'
			FROM products WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(VARIANT_A, PRODUCT_A));
		execute(TENANT_A_DATABASE, """
			INSERT INTO inventory_balances (variant_id, quantity)
			SELECT id, 5.000 FROM product_variants
			WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(VARIANT_A));
	}

	@Test
	void createsAnonymousPickupOrderFromServerPricesAndReservesStock()
			throws Exception {
		JsonNode created = create(UUID.randomUUID(), "2", 201);

		assertThat(created.at("/order/status").asText()).isEqualTo("PENDING_CONFIRMATION");
		assertThat(created.at("/order/fulfillmentType").asText()).isEqualTo("PICKUP");
		assertThat(created.at("/order/subtotal").asText()).isEqualTo("5000.00");
		assertThat(created.at("/order/items/0/unitPrice").asText()).isEqualTo("2500.00");
		assertThat(created.at("/order/items/0/quantity").asText()).isEqualTo("2.000");
		assertThat(created.at("/lookupToken").asText()).hasSize(43);
		assertThat(created.at("/order/customerPhone").isMissingNode()).isTrue();
		assertThat(created.at("/order/customerEmail").isMissingNode()).isTrue();
		assertThat(created.at("/order/items/0/sku").isMissingNode()).isTrue();
		assertThat(count(TENANT_A_DATABASE, "SELECT COUNT(*) FROM orders")).isEqualTo(1);
		assertThat(decimal(TENANT_A_DATABASE,
			"SELECT quantity FROM inventory_reservations")).isEqualTo("2.000");
		assertThat(decimal(TENANT_A_DATABASE,
			"SELECT quantity FROM inventory_balances")).isEqualTo("5.000");
	}

	@Test
	void replaysSameIntentRejectsChangedIntentAndPreventsOverselling()
			throws Exception {
		UUID key = UUID.randomUUID();
		JsonNode created = create(key, "3", 201);
		JsonNode replay = create(key, "3", 200);
		assertThat(replay.at("/order/id").asText())
			.isEqualTo(created.at("/order/id").asText());
		assertThat(replay.at("/lookupToken").asText())
			.isEqualTo(created.at("/lookupToken").asText());
		assertThat(replay.at("/replayed").asBoolean()).isTrue();

		mockMvc.perform(post(orders("tienda-a"))
				.with(csrf())
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("4")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type")
				.value("https://comercio-flex.local/problems/idempotency-conflict"));
		mockMvc.perform(post(orders("tienda-a"))
				.with(csrf())
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("3")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type")
				.value("https://comercio-flex.local/problems/order-item-unavailable"));
		assertThat(count(TENANT_A_DATABASE, "SELECT COUNT(*) FROM orders")).isEqualTo(1);
	}

	@Test
	void requiresPrivateLookupTokenAndExpiresReservationOnRead()
			throws Exception {
		UUID key = UUID.randomUUID();
		JsonNode created = create(key, "5", 201);
		String orderId = created.at("/order/id").asText();
		String token = created.at("/lookupToken").asText();

		mockMvc.perform(get(order("tienda-a", orderId)).param("token", "A".repeat(43)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail")
				.value("No existe un pedido accesible con esos datos."));
		mockMvc.perform(get(order("tienda-b", orderId)).param("token", token))
			.andExpect(status().isNotFound());
		execute(TENANT_A_DATABASE, """
			UPDATE orders SET reservation_expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND
			""");
		execute(TENANT_A_DATABASE, """
			UPDATE inventory_reservations SET expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND
			""");
		JsonNode replay = create(key, "5", 200);
		assertThat(replay.at("/order/status").asText()).isEqualTo("EXPIRED");
		assertThat(replay.at("/lookupToken").asText()).isEqualTo(token);
		mockMvc.perform(get(order("tienda-a", orderId)).param("token", token))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(jsonPath("$.status").value("EXPIRED"));
		assertThat(text(TENANT_A_DATABASE,
			"SELECT status FROM inventory_reservations")).isEqualTo("EXPIRED");
		mockMvc.perform(get("/api/v1/stores/tienda-a/catalog/products/asado"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.variants[0].available").value(true));
	}

	@Test
	void validatesCsrfHeaderPayloadAndUnavailableTenantScope() throws Exception {
		mockMvc.perform(post(orders("tienda-a"))
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("1")))
			.andExpect(status().isForbidden());
		mockMvc.perform(post(orders("tienda-a"))
				.with(csrf())
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("1.5")))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post(orders("tienda-b"))
				.with(csrf())
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("1")))
			.andExpect(status().isConflict());
		assertThat(count(TENANT_B_DATABASE, "SELECT COUNT(*) FROM orders")).isZero();
	}

	@RepeatedTest(3)
	void serializesConcurrentReservationsAndNeverExceedsPhysicalStock()
			throws Exception {
		var executor = Executors.newFixedThreadPool(2);
		try {
			var futures = executor.invokeAll(List.of(
				() -> createStatus(UUID.randomUUID(), "3"),
				() -> createStatus(UUID.randomUUID(), "3")));
			assertThat(futures)
				.extracting(Future::get)
				.containsExactlyInAnyOrder(201, 409);
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(decimal(TENANT_A_DATABASE, """
			SELECT COALESCE(SUM(quantity), 0.000)
			FROM inventory_reservations
			WHERE status = 'ACTIVE'
			""")).isEqualTo("3.000");
		assertThat(count(TENANT_A_DATABASE, "SELECT COUNT(*) FROM orders")).isEqualTo(1);
	}

	private JsonNode create(UUID key, String quantity, int status) throws Exception {
		MockHttpServletResponse response = mockMvc.perform(post(orders("tienda-a"))
				.with(csrf())
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(quantity)))
			.andExpect(status().is(status))
			.andExpect(header().string("Cache-Control", "no-store"))
			.andReturn()
			.getResponse();
		return objectMapper.readTree(response.getContentAsString());
	}

	private int createStatus(UUID key, String quantity) throws Exception {
		return mockMvc.perform(post(orders("tienda-a"))
				.with(csrf())
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(quantity)))
			.andReturn()
			.getResponse()
			.getStatus();
	}

	private String body(String quantity) {
		return """
			{
				"customerName": "  Ana   Pérez  ",
				"customerPhone": " 11 5555 1234 ",
				"customerEmail": "ANA@EXAMPLE.COM",
				"notes": " Cortado   fino ",
				"items": [{"variantId": "%s", "quantity": "%s"}]
			}
			""".formatted(VARIANT_A, quantity);
	}

	private String orders(String storeSlug) {
		return "/api/v1/stores/" + storeSlug + "/orders";
	}

	private String order(String storeSlug, String orderId) {
		return orders(storeSlug) + "/" + orderId;
	}

	private static void resetTenant(
			MySQLContainer<?> database,
			String storeName) throws SQLException {
		execute(database, "DELETE FROM inventory_reservations");
		execute(database, "DELETE FROM order_items");
		execute(database, "DELETE FROM orders");
		execute(database, "DELETE FROM inventory_movements");
		execute(database, "DELETE FROM inventory_balances");
		execute(database, "DELETE FROM product_variants");
		execute(database, "DELETE FROM products");
		execute(database, "DELETE FROM categories");
		execute(database, "DELETE FROM store_settings");
		execute(database, """
			INSERT INTO store_settings (store_name, currency_code, timezone)
			VALUES ('%s', 'ARS', 'America/Argentina/Buenos_Aires')
			""".formatted(storeName));
	}

	private static int count(MySQLContainer<?> database, String sql)
			throws SQLException {
		try (Connection connection = connection(database);
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery(sql)) {
			result.next();
			return result.getInt(1);
		}
	}

	private static String decimal(MySQLContainer<?> database, String sql)
			throws SQLException {
		try (Connection connection = connection(database);
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery(sql)) {
			result.next();
			return result.getBigDecimal(1).toPlainString();
		}
	}

	private static String text(MySQLContainer<?> database, String sql)
			throws SQLException {
		try (Connection connection = connection(database);
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery(sql)) {
			result.next();
			return result.getString(1);
		}
	}

	private static void execute(MySQLContainer<?> database, String sql)
			throws SQLException {
		try (Connection connection = connection(database);
				Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private static Connection connection(MySQLContainer<?> database)
			throws SQLException {
		return DriverManager.getConnection(
			database.getJdbcUrl(),
			database.getUsername(),
			database.getPassword());
	}

	private static void registerTenant(
			DynamicPropertyRegistry registry,
			String key,
			MySQLContainer<?> database) {
		String prefix = "app.database.tenant-connections." + key;
		registry.add(prefix + ".url", database::getJdbcUrl);
		registry.add(prefix + ".username", database::getUsername);
		registry.add(prefix + ".password", database::getPassword);
	}
}
