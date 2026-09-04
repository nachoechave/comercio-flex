package com.comercioflex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class InventoryManagementIntegrationTests {

	private static final DockerImageName MYSQL_IMAGE =
		DockerImageName.parse("mysql:8.4.10");
	private static final String PASSWORD = "correct-horse-battery-staple";
	private static final String PASSWORD_HASH =
		"{bcrypt}" + new BCryptPasswordEncoder(4).encode(PASSWORD);
	private static final String CATEGORY_A = "11111111-1111-4111-8111-111111111111";
	private static final String CATEGORY_B = "22222222-2222-4222-8222-222222222222";
	private static final String PRODUCT_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1";
	private static final String PRODUCT_A_ARCHIVED =
		"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2";
	private static final String PRODUCT_B = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1";
	private static final String VARIANT_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0101";
	private static final String VARIANT_A_INACTIVE =
		"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0102";
	private static final String VARIANT_A_ARCHIVED =
		"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0201";
	private static final String VARIANT_B = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0101";
	private static final String LONG_DISPLAY_NAME = "A".repeat(160);

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
	private DataSource controlDataSource;
	@Autowired
	private ObjectMapper objectMapper;

	private JdbcTemplate control;

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
		control = new JdbcTemplate(controlDataSource);
		control.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
		control.update("DELETE FROM SPRING_SESSION");
		control.update("DELETE FROM memberships");
		control.update("DELETE FROM platform_users");
		control.update("DELETE FROM tenants");
		control.update("""
			INSERT INTO tenants (public_id, slug, display_name, status, database_key)
			VALUES
				(UNHEX(REPLACE(UUID(), '-', '')), 'tienda-a', 'Tienda A', 'ACTIVE', 'tenant-a'),
				(UNHEX(REPLACE(UUID(), '-', '')), 'tienda-b', 'Tienda B', 'ACTIVE', 'tenant-b')
			""");
		insertUser("owner@example.com", LONG_DISPLAY_NAME, "OWNER", true);
		insertUser("admin@example.com", "Administrador", "ADMIN", false);
		insertUser("staff@example.com", "Operador", "STAFF", false);

		resetTenant(TENANT_A_DATABASE);
		resetTenant(TENANT_B_DATABASE);
		insertCategory(TENANT_A_DATABASE, CATEGORY_A, "Indumentaria", "indumentaria");
		insertCategory(TENANT_B_DATABASE, CATEGORY_B, "Indumentaria", "indumentaria");
		insertProduct(
			TENANT_A_DATABASE, PRODUCT_A, CATEGORY_A, "Remera", "remera", "PUBLISHED");
		insertVariant(
			TENANT_A_DATABASE, VARIANT_A, PRODUCT_A, "REM-ACTIVA", "M", "ACTIVE");
		insertVariant(
			TENANT_A_DATABASE,
			VARIANT_A_INACTIVE,
			PRODUCT_A,
			"REM-INACTIVA",
			"L",
			"INACTIVE");
		insertProduct(
			TENANT_A_DATABASE,
			PRODUCT_A_ARCHIVED,
			CATEGORY_A,
			"Campera archivada",
			"campera-archivada",
			"ARCHIVED");
		insertVariant(
			TENANT_A_DATABASE,
			VARIANT_A_ARCHIVED,
			PRODUCT_A_ARCHIVED,
			"CAMP-ARCH",
			"U",
			"ACTIVE");
		insertProduct(
			TENANT_B_DATABASE, PRODUCT_B, CATEGORY_B, "Remera B", "remera-b", "DRAFT");
		insertVariant(
			TENANT_B_DATABASE, VARIANT_B, PRODUCT_B, "REM-B", "M", "ACTIVE");
	}

	@Test
	void exposesLazyZeroInListDetailAndEmptyHistoryWithoutCreatingBalance()
			throws Exception {
		Auth owner = login("owner@example.com");

		mockMvc.perform(get(inventory("tienda-a"))
				.param("q", "REM-ACTIVA")
				.cookie(owner.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].variantId").value(VARIANT_A))
			.andExpect(jsonPath("$.items[0].quantity").value("0.000"))
			.andExpect(jsonPath("$.items[0].version").value(0))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalItems").value(1));
		mockMvc.perform(get(variant("tienda-a", VARIANT_A))
				.cookie(owner.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.quantity").value("0.000"))
			.andExpect(jsonPath("$.variantActive").value(true));
		mockMvc.perform(get(movements("tienda-a", VARIANT_A))
				.cookie(owner.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.totalItems").value(0));

		assertThat(count(TENANT_A_DATABASE, "SELECT COUNT(*) FROM inventory_balances"))
			.isZero();
	}

	@Test
	void exposesAndSearchesGenericVariantOptionsInInventory() throws Exception {
		Auth owner = login("owner@example.com");
		insertGenericOption(TENANT_A_DATABASE, PRODUCT_A, VARIANT_A,
			"Material", "material", "Algodón", "algodón");

		mockMvc.perform(get(inventory("tienda-a"))
				.param("q", "Algodón")
				.cookie(owner.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].variantId").value(VARIANT_A))
			.andExpect(jsonPath("$.items[0].sku").value("REM-ACTIVA"))
			.andExpect(jsonPath("$.items[0].options[0].name").value("Material"))
			.andExpect(jsonPath("$.items[0].options[0].value").value("Algodón"));

		mockMvc.perform(get(variant("tienda-a", VARIANT_A))
				.cookie(owner.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.options[0].name").value("Material"))
			.andExpect(jsonPath("$.options[0].value").value("Algodón"));
	}

	@Test
	void increasesTenThenDecreasesThreeAndReturnsCanonicalJsonStrings()
			throws Exception {
		Auth owner = login("owner@example.com");

		JsonNode increase = adjust(
			"tienda-a",
			VARIANT_A,
			UUID.randomUUID(),
			"INCREASE",
			"10",
			"RECEIPT",
			null,
			owner,
			201);
		assertThat(increase.at("/inventory/quantity").isTextual()).isTrue();
		assertThat(increase.at("/inventory/quantity").asText()).isEqualTo("10.000");
		assertThat(increase.at("/movement/delta").asText()).isEqualTo("10.000");
		assertThat(increase.at("/movement/quantityBefore").asText()).isEqualTo("0.000");
		assertThat(increase.at("/movement/quantityAfter").asText()).isEqualTo("10.000");

		JsonNode decrease = adjust(
			"tienda-a",
			VARIANT_A,
			UUID.randomUUID(),
			"DECREASE",
			"3.000",
			"CORRECTION",
			"Ajuste manual",
			owner,
			201);
		assertThat(decrease.at("/inventory/quantity").asText()).isEqualTo("7.000");
		assertThat(decrease.at("/movement/delta").asText()).isEqualTo("-3.000");
		assertThat(decrease.at("/movement/quantityBefore").asText()).isEqualTo("10.000");
		assertThat(decrease.at("/movement/quantityAfter").asText()).isEqualTo("7.000");

		mockMvc.perform(get(movements("tienda-a", VARIANT_A))
				.cookie(owner.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].quantityAfter").value("7.000"))
			.andExpect(jsonPath("$.items[1].quantityAfter").value("10.000"));
	}

	@Test
	void requiresAndNormalizesNoteForOtherReason() throws Exception {
		Auth owner = login("owner@example.com");

		mockMvc.perform(auth(post(adjustments("tienda-a", VARIANT_A)), owner)
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(adjustmentBody("INCREASE", "1", "OTHER", null)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type")
				.value("https://comercio-flex.local/problems/invalid-inventory-adjustment"));

		JsonNode response = adjust(
			"tienda-a",
			VARIANT_A,
			UUID.randomUUID(),
			"INCREASE",
			"1",
			"OTHER",
			"  Conteo   físico  ",
			owner,
			201);
		assertThat(response.at("/movement/note").asText()).isEqualTo("Conteo físico");
	}

	@Test
	void ownerAdminAndStaffCanReadAndAdjustButPostRequiresCsrf() throws Exception {
		for (String email : List.of(
				"owner@example.com",
				"admin@example.com",
				"staff@example.com")) {
			Auth auth = login(email);
			mockMvc.perform(get(inventory("tienda-a")).cookie(auth.session()))
				.andExpect(status().isOk());
			adjust(
				"tienda-a",
				VARIANT_A,
				UUID.randomUUID(),
				"INCREASE",
				"1",
				"RECEIPT",
				null,
				auth,
				201);
		}

		Auth admin = login("admin@example.com");
		mockMvc.perform(post(adjustments("tienda-a", VARIANT_A))
				.cookie(admin.session())
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(adjustmentBody("INCREASE", "1", "RECEIPT", null)))
			.andExpect(status().isForbidden());
	}

	@Test
	void isolatesTenantDataAndOpaqueVariantIds() throws Exception {
		Auth owner = login("owner@example.com");
		adjust(
			"tienda-a",
			VARIANT_A,
			UUID.randomUUID(),
			"INCREASE",
			"4",
			"RECEIPT",
			null,
			owner,
			201);

		mockMvc.perform(get(variant("tienda-b", VARIANT_A)).cookie(owner.session()))
			.andExpect(status().isNotFound());
		mockMvc.perform(get(inventory("tienda-b")).cookie(owner.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].variantId").value(VARIANT_B))
			.andExpect(jsonPath("$.items[0].quantity").value("0.000"));
		mockMvc.perform(auth(post(adjustments("tienda-b", VARIANT_A)), owner)
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(adjustmentBody("INCREASE", "1", "RECEIPT", null)))
			.andExpect(status().isNotFound());

		assertThat(count(TENANT_B_DATABASE, "SELECT COUNT(*) FROM inventory_balances"))
			.isZero();
	}

	@Test
	void permitsAdjustmentsForInactiveVariantAndArchivedProduct() throws Exception {
		Auth owner = login("owner@example.com");

		JsonNode inactive = adjust(
			"tienda-a",
			VARIANT_A_INACTIVE,
			UUID.randomUUID(),
			"INCREASE",
			"2",
			"RETURN",
			"Devolución tardía",
			owner,
			201);
		assertThat(inactive.at("/inventory/variantActive").asBoolean()).isFalse();
		assertThat(inactive.at("/inventory/quantity").asText()).isEqualTo("2.000");

		JsonNode archived = adjust(
			"tienda-a",
			VARIANT_A_ARCHIVED,
			UUID.randomUUID(),
			"INCREASE",
			"3",
			"CORRECTION",
			null,
			owner,
			201);
		assertThat(archived.at("/inventory/productStatus").asText())
			.isEqualTo("ARCHIVED");
		assertThat(archived.at("/inventory/quantity").asText()).isEqualTo("3.000");
	}

	@Test
	void rejectsNegativeBalanceAndRollsBackBalanceAndMovement() throws Exception {
		Auth owner = login("owner@example.com");
		adjust(
			"tienda-a", VARIANT_A, UUID.randomUUID(), "INCREASE", "5",
			"RECEIPT", null, owner, 201);

		mockMvc.perform(auth(post(adjustments("tienda-a", VARIANT_A)), owner)
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(adjustmentBody("DECREASE", "6", "DAMAGE", "Rotura")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type")
				.value("https://comercio-flex.local/problems/insufficient-stock"));

		assertThat(decimal(
			TENANT_A_DATABASE,
			"SELECT quantity FROM inventory_balances balance "
				+ "JOIN product_variants variant ON variant.id = balance.variant_id "
				+ "WHERE variant.public_id = UUID_TO_BIN('" + VARIANT_A + "')"))
			.isEqualTo("5.000");
		assertThat(count(TENANT_A_DATABASE, "SELECT COUNT(*) FROM inventory_movements"))
			.isEqualTo(1);
	}

	@Test
	void rejectsCapacityOverflowAndRollsBackBalanceAndMovement() throws Exception {
		Auth owner = login("owner@example.com");
		adjust(
			"tienda-a",
			VARIANT_A,
			UUID.randomUUID(),
			"INCREASE",
			"999999999999",
			"RECEIPT",
			null,
			owner,
			201);

		mockMvc.perform(auth(post(adjustments("tienda-a", VARIANT_A)), owner)
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(adjustmentBody("INCREASE", "1", "RECEIPT", null)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type")
				.value("https://comercio-flex.local/problems/inventory-capacity-exceeded"));

		assertThat(decimal(
			TENANT_A_DATABASE,
			"SELECT quantity FROM inventory_balances"))
			.isEqualTo("999999999999.000");
		assertThat(count(TENANT_A_DATABASE, "SELECT COUNT(*) FROM inventory_movements"))
			.isEqualTo(1);
	}

	@Test
	void recordsActorOnlyFromAuthenticatedServerPrincipalIncluding160Characters()
			throws Exception {
		Auth owner = login("owner@example.com");
		String expectedPublicId = text(
			CONTROL_DATABASE,
			"SELECT BIN_TO_UUID(public_id) FROM platform_users "
				+ "WHERE email_normalized = 'owner@example.com'");

		JsonNode response = adjust(
			"tienda-a",
			VARIANT_A,
			UUID.randomUUID(),
			"INCREASE",
			"1",
			"CORRECTION",
			"actor enviado por el servidor",
			owner,
			201);

		assertThat(response.at("/movement/actor/id").asText())
			.isEqualToIgnoringCase(expectedPublicId);
		assertThat(response.at("/movement/actor/displayName").asText())
			.isEqualTo(LONG_DISPLAY_NAME)
			.hasSize(160);
		assertThat(text(
			TENANT_A_DATABASE,
			"SELECT actor_display_name FROM inventory_movements"))
			.isEqualTo(LONG_DISPLAY_NAME);
	}

	@Test
	void replaysIdenticalIdempotentRequestAndRejectsPayloadMismatch()
			throws Exception {
		Auth owner = login("owner@example.com");
		UUID key = UUID.randomUUID();

		JsonNode created = adjust(
			"tienda-a", VARIANT_A, key, "INCREASE", "7", "RECEIPT",
			"Lote 1", owner, 201);
		JsonNode replay = adjust(
			"tienda-a", VARIANT_A, key, "INCREASE", "7.000", "RECEIPT",
			" Lote   1 ", owner, 200);
		assertThat(replay.at("/movement/id").asText())
			.isEqualTo(created.at("/movement/id").asText());
		assertThat(replay.at("/inventory/quantity").asText()).isEqualTo("7.000");

		mockMvc.perform(auth(post(adjustments("tienda-a", VARIANT_A)), owner)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(adjustmentBody("INCREASE", "8", "RECEIPT", "Lote 1")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type")
				.value("https://comercio-flex.local/problems/idempotency-conflict"));
		assertThat(count(TENANT_A_DATABASE, "SELECT COUNT(*) FROM inventory_movements"))
			.isEqualTo(1);
		assertThat(decimal(TENANT_A_DATABASE, "SELECT quantity FROM inventory_balances"))
			.isEqualTo("7.000");
	}

	@Test
	void serializesSameKeyConcurrentlyForSameAndDifferentVariants()
			throws Exception {
		Auth owner = login("owner@example.com");
		UUID sameVariantKey = UUID.randomUUID();
		var executor = Executors.newFixedThreadPool(2);
		try {
			List<Future<Integer>> sameVariant = executor.invokeAll(List.of(
				() -> adjustmentStatus(
					owner, VARIANT_A, sameVariantKey, "INCREASE", "10", "RECEIPT"),
				() -> adjustmentStatus(
					owner, VARIANT_A, sameVariantKey, "INCREASE", "10", "RECEIPT")));
			assertThat(sameVariant).extracting(Future::get)
				.containsExactlyInAnyOrder(201, 200);
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(decimal(TENANT_A_DATABASE, """
			SELECT quantity FROM inventory_balances balance
			JOIN product_variants variant ON variant.id = balance.variant_id
			WHERE variant.public_id = UUID_TO_BIN('%s')
			""".formatted(VARIANT_A))).isEqualTo("10.000");

		UUID differentVariantKey = UUID.randomUUID();
		var secondExecutor = Executors.newFixedThreadPool(2);
		try {
			List<Future<Integer>> differentVariants = secondExecutor.invokeAll(List.of(
				() -> adjustmentStatus(
					owner,
					VARIANT_A_INACTIVE,
					differentVariantKey,
					"INCREASE",
					"4",
					"RETURN"),
				() -> adjustmentStatus(
					owner,
					VARIANT_A_ARCHIVED,
					differentVariantKey,
					"INCREASE",
					"4",
					"RETURN")));
			assertThat(differentVariants).extracting(Future::get)
				.containsExactlyInAnyOrder(201, 409);
		}
		finally {
			secondExecutor.shutdownNow();
		}
		assertThat(count(
			TENANT_A_DATABASE,
			"SELECT COUNT(*) FROM inventory_movements "
				+ "WHERE idempotency_key = UUID_TO_BIN('" + differentVariantKey + "')"))
			.isEqualTo(1);
		assertThat(decimal(
			TENANT_A_DATABASE,
			"SELECT COALESCE(SUM(quantity), 0) FROM inventory_balances balance "
				+ "JOIN product_variants variant ON variant.id = balance.variant_id "
				+ "WHERE variant.public_id IN (UUID_TO_BIN('" + VARIANT_A_INACTIVE
				+ "'), UUID_TO_BIN('" + VARIANT_A_ARCHIVED + "'))"))
			.isEqualTo("4.000");
	}

	@Test
	void accumulatesConcurrentIncrementsAndPreventsConcurrentNegativeStock()
			throws Exception {
		Auth owner = login("owner@example.com");
		runConcurrentAdjustments(owner, VARIANT_A, 12, "INCREASE", "2", 12, 0);
		assertThat(currentQuantity(VARIANT_A)).isEqualTo("24.000");

		runConcurrentAdjustments(owner, VARIANT_A, 10, "DECREASE", "3", 8, 2);
		assertThat(currentQuantity(VARIANT_A)).isEqualTo("0.000");
		assertThat(count(TENANT_A_DATABASE, """
			SELECT COUNT(*) FROM inventory_balances WHERE quantity < 0
			""")).isZero();
		assertThat(count(TENANT_A_DATABASE, "SELECT COUNT(*) FROM inventory_movements"))
			.isEqualTo(20);
	}

	@Test
	void paginatesFiltersHistoryAndRejectsParameterAndPayloadLimits()
			throws Exception {
		Auth owner = login("owner@example.com");
		adjust(
			"tienda-a", VARIANT_A, UUID.randomUUID(), "INCREASE", "2",
			"RECEIPT", null, owner, 201);
		adjust(
			"tienda-a", VARIANT_A, UUID.randomUUID(), "DECREASE", "1",
			"CORRECTION", null, owner, 201);

		mockMvc.perform(get(inventory("tienda-a"))
				.param("availability", "IN_STOCK")
				.param("page", "0")
				.param("size", "1")
				.cookie(owner.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.totalPages").value(1));
		mockMvc.perform(get(inventory("tienda-a"))
				.param("availability", "OUT_OF_STOCK")
				.param("size", "2")
				.cookie(owner.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.totalItems").value(2))
			.andExpect(jsonPath("$.totalPages").value(1));
		mockMvc.perform(get(movements("tienda-a", VARIANT_A))
				.param("page", "0")
				.param("size", "1")
				.cookie(owner.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.totalItems").value(2))
			.andExpect(jsonPath("$.totalPages").value(2));

		for (String path : List.of(
				inventory("tienda-a"),
				movements("tienda-a", VARIANT_A))) {
			mockMvc.perform(get(path).param("size", "101").cookie(owner.session()))
				.andExpect(status().isBadRequest());
			mockMvc.perform(get(path).param("page", "1000001").cookie(owner.session()))
				.andExpect(status().isBadRequest());
		}
		mockMvc.perform(get(inventory("tienda-a"))
				.param("q", "x".repeat(101))
				.cookie(owner.session()))
			.andExpect(status().isBadRequest());
		mockMvc.perform(auth(post(adjustments("tienda-a", VARIANT_A)), owner)
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(adjustmentBody("INCREASE", "1.001", "RECEIPT", null)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void dashboardAggregatesOnlyCurrentValidSalesAndIsolatesTenantData()
			throws Exception {
		insertDashboardOrder(TENANT_A_DATABASE, "CONFIRMED", "1250.50");
		insertDashboardOrder(TENANT_A_DATABASE, "CANCELLED", "900.00");
		Auth owner = login("owner@example.com");

		mockMvc.perform(get(dashboard("tienda-a")).cookie(owner.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.currencyCode").value("ARS"))
			.andExpect(jsonPath("$.timezone")
				.value("America/Argentina/Buenos_Aires"))
			.andExpect(jsonPath("$.salesToday").value("1250.50"))
			.andExpect(jsonPath("$.salesThisMonth").value("1250.50"))
			.andExpect(jsonPath("$.openOrders").value(1))
			.andExpect(jsonPath("$.lowStockVariants").value(1))
			.andExpect(jsonPath("$.criticalStock[0].variantId").value(VARIANT_A))
			.andExpect(jsonPath("$.criticalStock[0].quantity").value("0.000"));

		mockMvc.perform(get(dashboard("tienda-b")).cookie(owner.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.salesToday").value("0.00"))
			.andExpect(jsonPath("$.openOrders").value(0))
			.andExpect(jsonPath("$.criticalStock[0].variantId").value(VARIANT_B));
	}

	@Test
	void dashboardSettingsRequirePermissionAndValidateThreshold() throws Exception {
		Auth admin = login("admin@example.com");
		mockMvc.perform(put(dashboard("tienda-a") + "/settings")
				.cookie(admin.session(), admin.csrf())
				.header("X-XSRF-TOKEN", admin.csrf().getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lowStockThreshold\": \"2.750\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.lowStockThreshold").value("2.750"));
		assertThat(decimal(TENANT_A_DATABASE,
			"SELECT low_stock_threshold FROM store_settings LIMIT 1"))
			.isEqualTo("2.750");

		mockMvc.perform(put(dashboard("tienda-a") + "/settings")
				.cookie(admin.session(), admin.csrf())
				.header("X-XSRF-TOKEN", admin.csrf().getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lowStockThreshold\": \"-1.000\"}"))
			.andExpect(status().isBadRequest());

		Auth staff = login("staff@example.com");
		mockMvc.perform(get(dashboard("tienda-a")).cookie(staff.session()))
			.andExpect(status().isForbidden());
		mockMvc.perform(put(dashboard("tienda-a") + "/settings")
				.cookie(staff.session(), staff.csrf())
				.header("X-XSRF-TOKEN", staff.csrf().getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lowStockThreshold\": \"3.000\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void storeSettingsAreTenantScopedEditableByOwnerAndAdminButVisualBrandingStaysPlatformManaged() throws Exception {
		Auth admin = login("admin@example.com");
		String body = """
			{
			  "storeName": "La Esquina",
			  "contactPhone": "+54 11 4444-5555",
			  "contactEmail": "ventas@laesquina.test",
			  "pickupAddress": "Av. Siempre Viva 742",
			  "pickupInstructions": "Retirar por el mostrador principal."
			}
			""";

		mockMvc.perform(auth(put(storeSettings("tienda-a")), admin)
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.storeName").value("La Esquina"))
			.andExpect(jsonPath("$.pickupAddress").value("Av. Siempre Viva 742"))
			.andExpect(jsonPath("$.brandTheme").value("VIOLET"))
			.andExpect(jsonPath("$.branding.primaryColor").value("#6D3CE7"));

		mockMvc.perform(get("/api/v1/stores/tienda-a/settings"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contactPhone").value("+54 11 4444-5555"))
			.andExpect(jsonPath("$.contactEmail").value("ventas@laesquina.test"));
		mockMvc.perform(get("/api/v1/stores/tienda-b/settings"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.storeName").value("Tienda de prueba"))
			.andExpect(jsonPath("$.brandTheme").value("VIOLET"));

		Auth staff = login("staff@example.com");
		mockMvc.perform(get(storeSettings("tienda-a")).cookie(staff.session()))
			.andExpect(status().isForbidden());

		mockMvc.perform(put(storeSettings("tienda-a")).cookie(admin.session())
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isForbidden());
		mockMvc.perform(auth(put(storeSettings("tienda-a")), staff)
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isForbidden());
	}

	@Test
	void storeSettingsRequireValidContactAndPickupAddress() throws Exception {
		Auth owner = login("owner@example.com");
		String invalid = """
			{"storeName":" A ","contactPhone":"","contactEmail":"",
			 "pickupAddress":" x ","pickupInstructions":""}
			""";
		mockMvc.perform(auth(put(storeSettings("tienda-a")), owner)
				.contentType(MediaType.APPLICATION_JSON).content(invalid))
			.andExpect(status().isBadRequest());
	}

	private void runConcurrentAdjustments(
			Auth auth,
			String variantId,
			int requests,
			String direction,
			String quantity,
			int expectedCreated,
			int expectedConflicts) throws Exception {
		var executor = Executors.newFixedThreadPool(requests);
		try {
			List<Callable<Integer>> tasks = new ArrayList<>();
			for (int index = 0; index < requests; index++) {
				tasks.add(() -> adjustmentStatus(
					auth,
					variantId,
					UUID.randomUUID(),
					direction,
					quantity,
					"CORRECTION"));
			}
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> future : executor.invokeAll(tasks)) {
				statuses.add(future.get());
			}
			assertThat(statuses).filteredOn(code -> code == 201)
				.hasSize(expectedCreated);
			assertThat(statuses).filteredOn(code -> code == 409)
				.hasSize(expectedConflicts);
			assertThat(statuses).allMatch(code -> code == 201 || code == 409);
		}
		finally {
			executor.shutdownNow();
		}
	}

	private JsonNode adjust(
			String store,
			String variantId,
			UUID key,
			String direction,
			String quantity,
			String reason,
			String note,
			Auth auth,
			int expectedStatus) throws Exception {
		MockHttpServletResponse response = mockMvc.perform(
				auth(post(adjustments(store, variantId)), auth)
					.header("Idempotency-Key", key)
					.contentType(MediaType.APPLICATION_JSON)
					.content(adjustmentBody(direction, quantity, reason, note)))
			.andExpect(status().is(expectedStatus))
			.andReturn()
			.getResponse();
		return objectMapper.readTree(response.getContentAsString());
	}

	private int adjustmentStatus(
			Auth auth,
			String variantId,
			UUID key,
			String direction,
			String quantity,
			String reason) throws Exception {
		return mockMvc.perform(auth(post(adjustments("tienda-a", variantId)), auth)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(adjustmentBody(direction, quantity, reason, null)))
			.andReturn().getResponse().getStatus();
	}

	private String adjustmentBody(
			String direction,
			String quantity,
			String reason,
			String note) throws Exception {
		var body = objectMapper.createObjectNode()
			.put("direction", direction)
			.put("quantity", quantity)
			.put("reason", reason);
		if (note != null) {
			body.put("note", note);
		}
		return objectMapper.writeValueAsString(body);
	}

	private Auth login(String email) throws Exception {
		Cookie initial = requiredCookie(mockMvc.perform(get("/api/v1/auth/csrf"))
			.andExpect(status().isOk()).andReturn().getResponse(), "XSRF-TOKEN");
		MockHttpServletResponse response = mockMvc.perform(post("/api/v1/auth/login")
				.cookie(initial)
				.header("X-XSRF-TOKEN", initial.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"%s"}
					""".formatted(email, PASSWORD)))
			.andExpect(status().isOk()).andReturn().getResponse();
		return new Auth(
			requiredCookie(response, "CFSESSION"),
			requiredCookie(response, "XSRF-TOKEN"));
	}

	private MockHttpServletRequestBuilder auth(
			MockHttpServletRequestBuilder request,
			Auth auth) {
		return request.cookie(auth.session(), auth.csrf())
			.header("X-XSRF-TOKEN", auth.csrf().getValue());
	}

	private String inventory(String store) {
		return "/api/v1/stores/" + store + "/admin/inventory";
	}

	private String dashboard(String store) {
		return "/api/v1/stores/" + store + "/admin/dashboard";
	}

	private String storeSettings(String store) {
		return "/api/v1/stores/" + store + "/admin/settings";
	}

	private String variant(String store, String variantId) {
		return inventory(store) + "/variants/" + variantId;
	}

	private String movements(String store, String variantId) {
		return variant(store, variantId) + "/movements";
	}

	private String adjustments(String store, String variantId) {
		return variant(store, variantId) + "/adjustments";
	}

	private String currentQuantity(String variantId) throws SQLException {
		return decimal(TENANT_A_DATABASE, """
			SELECT quantity
			FROM inventory_balances balance
			JOIN product_variants variant ON variant.id = balance.variant_id
			WHERE variant.public_id = UUID_TO_BIN('%s')
			""".formatted(variantId));
	}

	private void insertUser(
			String email,
			String displayName,
			String role,
			boolean bothStores) {
		control.update("""
			INSERT INTO platform_users
				(public_id, email_normalized, display_name, password_hash, status)
			VALUES (UNHEX(REPLACE(UUID(), '-', '')), ?, ?, ?, 'ACTIVE')
			""",
			email,
			displayName,
			PASSWORD_HASH);
		insertMembership(email, role, "tienda-a");
		if (bothStores) {
			insertMembership(email, role, "tienda-b");
		}
	}

	private void insertMembership(String email, String role, String store) {
		control.update("""
			INSERT INTO memberships (user_id, tenant_id, role, status)
			SELECT user.id, tenant.id, ?, 'ACTIVE'
			FROM platform_users user, tenants tenant
			WHERE user.email_normalized = ? AND tenant.slug = ?
			""",
			role,
			email,
			store);
	}

	private static void resetTenant(MySQLContainer<?> database) throws SQLException {
		execute(database, "DELETE FROM order_status_history");
		execute(database, "DELETE FROM inventory_reservations");
		execute(database, "DELETE FROM order_items");
		execute(database, "DELETE FROM inventory_movements");
		execute(database, "DELETE FROM orders");
		execute(database, "DELETE FROM inventory_balances");
		execute(database, "DELETE FROM product_variant_option_values");
		execute(database, "DELETE FROM product_option_values");
		execute(database, "DELETE FROM product_options");
		execute(database, "DELETE FROM product_variants");
		execute(database, "DELETE FROM products");
		execute(database, "DELETE FROM categories");
		execute(database, "DELETE FROM store_settings");
		execute(database, """
			INSERT INTO store_settings (store_name, currency_code, timezone)
			VALUES ('Tienda de prueba', 'ARS', 'America/Argentina/Buenos_Aires')
			""");
	}

	private static void insertDashboardOrder(
			MySQLContainer<?> database,
			String status,
			String subtotal) throws SQLException {

		execute(database, """
				INSERT INTO orders (
						public_id,
						idempotency_key,
						request_fingerprint,
						lookup_token_hash,
						status,
						fulfillment_type,
						customer_name,
						customer_phone,
						currency_code,
						payment_method,
						list_subtotal,
						subtotal,
						reservation_expires_at
				)
				VALUES (
						UUID_TO_BIN(UUID()),
						UUID_TO_BIN(UUID()),
						RANDOM_BYTES(32),
						RANDOM_BYTES(32),
						'%s',
						'PICKUP',
						'Cliente dashboard',
						'11111111',
						'ARS',
						'MERCADO_PAGO',
						%s,
						%s,
						DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR)
				)
				""".formatted(
						status,
						subtotal,
						subtotal));

		execute(database, """
				INSERT INTO order_status_history (
						public_id,
						order_id,
						previous_status,
						new_status,
						actor_display_name,
						created_at
				)
				SELECT
						UUID_TO_BIN(UUID()),
						MAX(id),
						'PENDING_CONFIRMATION',
						'CONFIRMED',
						'Sistema',
						UTC_TIMESTAMP(6)
				FROM orders
				""");
	}

	private static void insertCategory(
			MySQLContainer<?> database,
			String publicId,
			String name,
			String slug) throws SQLException {
		execute(database, """
			INSERT INTO categories (public_id, name, slug, status)
			VALUES (UUID_TO_BIN('%s'), '%s', '%s', 'ACTIVE')
			""".formatted(publicId, name, slug));
	}

	private static void insertProduct(
			MySQLContainer<?> database,
			String publicId,
			String categoryId,
			String name,
			String slug,
			String status) throws SQLException {
		execute(database, """
			INSERT INTO products (public_id, category_id, name, slug, status)
			SELECT UUID_TO_BIN('%s'), id, '%s', '%s', '%s'
			FROM categories
			WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(publicId, name, slug, status, categoryId));
	}

	private static void insertVariant(
			MySQLContainer<?> database,
			String publicId,
			String productId,
			String sku,
			String size,
			String status) throws SQLException {
		execute(database, """
			INSERT INTO product_variants
				(public_id, product_id, sku, price, size_value, option_signature, status)
			SELECT UUID_TO_BIN('%s'), id, '%s', 100.00, '%s', SHA2(UUID(), 256), '%s'
			FROM products
			WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(publicId, sku, size, status, productId));
	}

	private static void insertGenericOption(
			MySQLContainer<?> database,
			String productId,
			String variantId,
			String optionName,
			String normalizedOption,
			String value,
			String normalizedValue) throws SQLException {
		execute(database, """
			INSERT INTO product_options
				(public_id, product_id, name, normalized_name, position)
			SELECT UUID_TO_BIN(UUID()), product.id, '%s', '%s',
				COALESCE((SELECT MAX(existing.position) + 1
					FROM product_options existing
					WHERE existing.product_id = product.id), 1)
			FROM products product WHERE product.public_id = UUID_TO_BIN('%s')
			""".formatted(optionName, normalizedOption, productId));
		execute(database, """
			INSERT INTO product_option_values
				(public_id, option_id, value, normalized_value, position)
			SELECT UUID_TO_BIN(UUID()), id, '%s', '%s', 1
			FROM product_options
			WHERE product_id = (SELECT id FROM products WHERE public_id = UUID_TO_BIN('%s'))
				AND normalized_name = '%s'
			""".formatted(value, normalizedValue, productId, normalizedOption));
		execute(database, """
			INSERT INTO product_variant_option_values (variant_id, option_value_id)
			SELECT variant.id, option_value.id
			FROM product_variants variant
			JOIN product_options product_option ON product_option.product_id = variant.product_id
			JOIN product_option_values option_value ON option_value.option_id = product_option.id
			WHERE variant.public_id = UUID_TO_BIN('%s')
				AND product_option.normalized_name = '%s'
			""".formatted(variantId, normalizedOption));
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

	private static Cookie requiredCookie(MockHttpServletResponse response, String name) {
		Cookie cookie = response.getCookie(name);
		if (cookie == null) {
			throw new AssertionError("Missing cookie " + name);
		}
		return cookie;
	}

	private record Auth(Cookie session, Cookie csrf) {
	}
}
