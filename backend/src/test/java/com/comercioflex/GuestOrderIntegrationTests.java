package com.comercioflex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.comercioflex.payment.application.CheckoutRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.comercioflex.order.application.AdminOrderNotFoundException;
import com.comercioflex.order.application.AdminOrderService;
import com.comercioflex.order.application.InvalidOrderTransitionException;
import com.comercioflex.order.application.OrderTransitionCommand;
import com.comercioflex.order.application.PaidOrderConfirmer;
import com.comercioflex.order.domain.OrderStatus;
import com.comercioflex.payment.application.GatewayPayment;
import com.comercioflex.payment.application.PaymentApplicationService;
import com.comercioflex.payment.application.PaymentCommand;
import com.comercioflex.payment.application.PaymentConflictException;
import com.comercioflex.payment.application.PaymentGateway;
import com.comercioflex.payment.application.PaymentInitiation;
import com.comercioflex.payment.application.PaymentRepository;
import com.comercioflex.payment.domain.PaymentIntentStatus;
import com.comercioflex.payment.domain.PaymentProvider;
import com.comercioflex.payment.domain.PaymentResultStatus;
import com.comercioflex.payment.infrastructure.fake.FakePaymentGateway;
import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.identity.application.UserCredentials;
import com.comercioflex.identity.domain.UserStatus;
import com.comercioflex.tenant.application.TenantContext;

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
	private static final UUID OPERATOR_ID =
		UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee");

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
	@Autowired
	private AdminOrderService adminOrderService;
	@Autowired
	private PaidOrderConfirmer paidOrderConfirmer;
	@Autowired
	private PaymentRepository paymentRepository;
	@Autowired
	@Qualifier("tenantTransactionTemplate")
	private TransactionTemplate tenantTransactionTemplate;
	@Autowired
	private TenantContext tenantContext;
	@Autowired
	private CheckoutRepository checkoutRepository;

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
		execute(CONTROL_DATABASE, """
			INSERT INTO platform_users (
				id, public_id, email_normalized, display_name, password_hash, status
			)
			VALUES (
				9001,
				UUID_TO_BIN('%s'),
				'operator@example.test',
				'Operador',
				'{noop}test',
				'ACTIVE'
			)
			""".formatted(OPERATOR_ID));
		execute(CONTROL_DATABASE, """
			INSERT INTO memberships (user_id, tenant_id, role, status)
			SELECT 9001, id, 'STAFF', 'ACTIVE'
			FROM tenants
			WHERE slug = 'tienda-a'
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
				public_id, product_id, sku, price, size_value, color_value,
				option_signature, status
			)
			SELECT UUID_TO_BIN('%s'), id, 'ASA-001', 2500.00, '', '',
				SHA2('corte=tradicional', 256), 'ACTIVE'
			FROM products WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(VARIANT_A, PRODUCT_A));
		execute(TENANT_A_DATABASE, """
			INSERT INTO product_options (public_id, product_id, name, normalized_name, position)
			SELECT UUID_TO_BIN(UUID()), id, 'Corte', 'corte', 1
			FROM products WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(PRODUCT_A));
		execute(TENANT_A_DATABASE, """
			INSERT INTO product_option_values (
				public_id, option_id, value, normalized_value, position
			)
			SELECT UUID_TO_BIN(UUID()), id, 'Tradicional', 'tradicional', 1
			FROM product_options WHERE product_id = (
				SELECT id FROM products WHERE public_id = UUID_TO_BIN('%s')
			)
			""".formatted(PRODUCT_A));
		execute(TENANT_A_DATABASE, """
			INSERT INTO product_variant_option_values (variant_id, option_value_id)
			SELECT variant.id, option_value.id
			FROM product_variants variant
			JOIN product_options product_option ON product_option.product_id = variant.product_id
			JOIN product_option_values option_value ON option_value.option_id = product_option.id
			WHERE variant.public_id = UUID_TO_BIN('%s')
			""".formatted(VARIANT_A));
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
		assertThat(created.at("/order/items/0/options/0/name").asText()).isEqualTo("Corte");
		assertThat(created.at("/order/items/0/options/0/value").asText())
			.isEqualTo("Tradicional");
		assertThat(text(TENANT_A_DATABASE,
			"SELECT JSON_UNQUOTE(JSON_EXTRACT(options_snapshot, '$[0].value')) FROM order_items"))
			.isEqualTo("Tradicional");
		assertThat(created.at("/lookupToken").asText()).hasSize(43);
		assertThat(created.at("/order/customerPhone").isMissingNode()).isTrue();
		assertThat(created.at("/order/customerEmail").isMissingNode()).isTrue();
		assertThat(text(TENANT_A_DATABASE,
			"SELECT customer_email FROM orders")).isEqualTo("ana@example.com");
		assertThat(created.at("/order/items/0/sku").isMissingNode()).isTrue();
		assertThat(count(TENANT_A_DATABASE, "SELECT COUNT(*) FROM orders")).isEqualTo(1);
		assertThat(decimal(TENANT_A_DATABASE,
			"SELECT quantity FROM inventory_reservations")).isEqualTo("2.000");
		assertThat(decimal(TENANT_A_DATABASE,
			"SELECT quantity FROM inventory_balances")).isEqualTo("5.000");
	}

	@Test
	void checkoutProRepositoryOnlyLocksMercadoPagoOrders() throws Exception {
			JsonNode created = create(UUID.randomUUID(), "1", 201);

			UUID orderId = UUID.fromString(created.at("/order/id").asText());
			String lookupToken = created.at("/lookupToken").asText();
			byte[] lookupTokenHash = sha256(lookupToken);

			try (var scope = tenantContext.open("tenant-a")) {
					var mercadoPagoOrder = tenantTransactionTemplate.execute(
							status -> checkoutRepository.lockOrder(
									orderId,
									lookupTokenHash));

					assertThat(mercadoPagoOrder).isPresent();
			}

			execute(TENANT_A_DATABASE, """
					UPDATE orders
					SET payment_method = 'BANK_TRANSFER'
					WHERE public_id = UUID_TO_BIN('%s')
					""".formatted(orderId));

			try (var scope = tenantContext.open("tenant-a")) {
					var bankTransferOrder = tenantTransactionTemplate.execute(
							status -> checkoutRepository.lockOrder(
									orderId,
									lookupTokenHash));

					assertThat(bankTransferOrder).isEmpty();
			}
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
			.andExpect(jsonPath("$.variants[0].available").value(true))
			.andExpect(jsonPath("$.variants[0].options[0].name").value("Corte"))
			.andExpect(jsonPath("$.variants[0].options[0].value").value("Tradicional"));
	}

	@Test
	void activeMercadoPagoAttemptDefersClockExpiryButDoesNotHoldStockIndefinitely()
			throws Exception {
		JsonNode created = create(UUID.randomUUID(), "1", 201);
		UUID orderId = UUID.fromString(created.at("/order/id").asText());
		String token = created.at("/lookupToken").asText();
		try (var scope = tenantContext.open("tenant-a")) {
			paymentService(PaymentResultStatus.PENDING)
				.initiate(new PaymentCommand(orderId, UUID.randomUUID()));
		}
		execute(TENANT_A_DATABASE, """
			UPDATE payment_intents
			SET provider = 'MERCADO_PAGO',
				return_token_hash = UNHEX(SHA2(UUID(), 256)),
				return_token_expires_at = UTC_TIMESTAMP(6) + INTERVAL 1 DAY,
				provider_preference_id = CONCAT('pref-', UUID()),
				checkout_url = 'https://www.mercadopago.com.ar/checkout',
				checkout_expires_at = UTC_TIMESTAMP(6) + INTERVAL 1 HOUR,
				credential_seller_account_id = 'seller-123',
				payment_environment = 'PRODUCTION',
				preference_created_at = UTC_TIMESTAMP(6)
			""");
		execute(TENANT_A_DATABASE, """
			UPDATE payment_transactions SET provider = 'MERCADO_PAGO'
			""");
		execute(TENANT_A_DATABASE, """
			UPDATE orders SET reservation_expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND
			""");
		execute(TENANT_A_DATABASE, """
			UPDATE inventory_reservations SET expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND
			""");

		mockMvc.perform(get(order("tienda-a", orderId.toString())).param("token", token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"));
		assertThat(text(TENANT_A_DATABASE,
			"SELECT status FROM inventory_reservations")).isEqualTo("ACTIVE");

		execute(TENANT_A_DATABASE, """
			UPDATE payment_intents
			SET checkout_expires_at = UTC_TIMESTAMP(6) - INTERVAL 25 HOUR
			""");
		mockMvc.perform(get(order("tienda-a", orderId.toString())).param("token", token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("EXPIRED"));
		assertThat(text(TENANT_A_DATABASE,
			"SELECT status FROM inventory_reservations")).isEqualTo("EXPIRED");
	}

	@Test
	void operatesOrderLifecycleAndRestoresStockOnCancellation() throws Exception {
		JsonNode created = create(UUID.randomUUID(), "2", 201);
		UUID orderId = UUID.fromString(created.at("/order/id").asText());
		UUID actorId = UUID.randomUUID();
		UUID confirmationKey = UUID.randomUUID();

		try (var scope = tenantContext.open("tenant-a")) {
			assertThat(adminOrderService.find(orderId).history()).hasSize(1);
			var confirmed = adminOrderService.transition(new OrderTransitionCommand(
				orderId,
				confirmationKey,
				OrderStatus.CONFIRMED,
				"Stock revisado",
				actorId,
				"Operador"));
			assertThat(confirmed.status()).isEqualTo(OrderStatus.CONFIRMED);
			assertThat(adminOrderService.transition(new OrderTransitionCommand(
				orderId,
				confirmationKey,
				OrderStatus.CONFIRMED,
				"Stock revisado",
				actorId,
				"Operador")).status()).isEqualTo(OrderStatus.CONFIRMED);
			adminOrderService.transition(new OrderTransitionCommand(
				orderId,
				UUID.randomUUID(),
				OrderStatus.READY_FOR_PICKUP,
				null,
				actorId,
				"Operador"));
			var cancelled = adminOrderService.transition(new OrderTransitionCommand(
				orderId,
				UUID.randomUUID(),
				OrderStatus.CANCELLED,
				"Cliente canceló",
				actorId,
				"Operador"));
			assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
			assertThat(cancelled.history()).hasSize(4);
		}
		try (var scope = tenantContext.open("tenant-b")) {
			assertThatThrownBy(() -> adminOrderService.find(orderId))
				.isInstanceOf(AdminOrderNotFoundException.class);
		}

		assertThat(decimal(TENANT_A_DATABASE,
			"SELECT quantity FROM inventory_balances")).isEqualTo("5.000");
		assertThat(count(TENANT_A_DATABASE,
			"SELECT COUNT(*) FROM inventory_movements")).isEqualTo(2);
		assertThat(text(TENANT_A_DATABASE,
			"SELECT status FROM inventory_reservations")).isEqualTo("RELEASED");
	}

	@Test
	void approvedFakePaymentConfirmsOnceAndProtectsPaidCancellation()
			throws Exception {
		JsonNode created = create(UUID.randomUUID(), "2", 201);
		UUID orderId = UUID.fromString(created.at("/order/id").asText());
		UUID key = UUID.randomUUID();
		PaymentApplicationService payments = paymentService(PaymentResultStatus.APPROVED);

		try (var scope = tenantContext.open("tenant-a")) {
			var first = payments.initiate(new PaymentCommand(orderId, key));
			var replay = payments.initiate(new PaymentCommand(orderId, key));
			assertThat(first.paymentIntent().status())
				.isEqualTo(PaymentIntentStatus.APPROVED);
			assertThat(replay.paymentIntent().id())
				.isEqualTo(first.paymentIntent().id());
			assertThat(replay.replayed()).isTrue();
			assertThatThrownBy(() -> adminOrderService.transition(
				new OrderTransitionCommand(
					orderId,
					UUID.randomUUID(),
					OrderStatus.CANCELLED,
					null,
					OPERATOR_ID,
					"Operador")))
				.isInstanceOf(InvalidOrderTransitionException.class)
				.hasMessageContaining("reembolso");
		}

		assertThat(text(TENANT_A_DATABASE, "SELECT status FROM orders"))
			.isEqualTo("CONFIRMED");
		assertThat(decimal(TENANT_A_DATABASE,
			"SELECT quantity FROM inventory_balances")).isEqualTo("3.000");
		assertThat(text(TENANT_A_DATABASE,
			"SELECT status FROM inventory_reservations")).isEqualTo("CONSUMED");
		assertThat(count(TENANT_A_DATABASE,
			"SELECT COUNT(*) FROM payment_intents")).isEqualTo(1);
		assertThat(count(TENANT_A_DATABASE,
			"SELECT COUNT(*) FROM payment_transactions")).isEqualTo(1);
		assertThat(count(TENANT_A_DATABASE,
			"SELECT COUNT(*) FROM inventory_movements")).isEqualTo(1);
	}

	@Test
	void pendingAndRejectedPaymentsDoNotChangeOrderOrPhysicalStock()
			throws Exception {
		UUID pendingOrderId = UUID.fromString(
			create(UUID.randomUUID(), "1", 201).at("/order/id").asText());
		try (var scope = tenantContext.open("tenant-a")) {
			var pending = paymentService(PaymentResultStatus.PENDING)
				.initiate(new PaymentCommand(pendingOrderId, UUID.randomUUID()));
			assertThat(pending.paymentIntent().status())
				.isEqualTo(PaymentIntentStatus.PENDING);
			assertThatThrownBy(() -> paymentService(PaymentResultStatus.APPROVED)
				.initiate(new PaymentCommand(pendingOrderId, UUID.randomUUID())))
				.isInstanceOf(PaymentConflictException.class)
				.hasMessageContaining("pago activo");
		}

		UUID rejectedOrderId = UUID.fromString(
			create(UUID.randomUUID(), "1", 201).at("/order/id").asText());
		try (var scope = tenantContext.open("tenant-a")) {
			var rejected = paymentService(PaymentResultStatus.REJECTED)
				.initiate(new PaymentCommand(rejectedOrderId, UUID.randomUUID()));
			assertThat(rejected.paymentIntent().status())
				.isEqualTo(PaymentIntentStatus.REJECTED);
		}

		assertThat(count(TENANT_A_DATABASE, """
			SELECT COUNT(*) FROM orders WHERE status = 'PENDING_CONFIRMATION'
			""")).isEqualTo(2);
		assertThat(decimal(TENANT_A_DATABASE,
			"SELECT quantity FROM inventory_balances")).isEqualTo("5.000");
		assertThat(count(TENANT_A_DATABASE,
			"SELECT COUNT(*) FROM inventory_movements")).isZero();
		assertThatThrownBy(() -> execute(TENANT_A_DATABASE, """
			INSERT INTO payment_intents (
				public_id, order_id, idempotency_key, request_fingerprint,
				transition_idempotency_key, provider, status, attempt_number,
				amount, currency_code, external_reference
			)
			SELECT
				UUID_TO_BIN(UUID()), order_id, UUID_TO_BIN(UUID()), request_fingerprint,
				UUID_TO_BIN(UUID()), provider, 'CREATED', attempt_number + 10,
				amount, currency_code, CONCAT('duplicate-', UUID())
			FROM payment_intents
			WHERE status = 'PENDING'
			"""))
			.isInstanceOf(SQLException.class);
		assertThatThrownBy(() -> execute(TENANT_A_DATABASE, """
			UPDATE payment_intents SET currency_code = 'ars' LIMIT 1
			"""))
			.isInstanceOf(SQLException.class);
		assertThatThrownBy(() -> execute(TENANT_A_DATABASE, """
			UPDATE payment_transactions SET currency_code = 'ars' LIMIT 1
			"""))
			.isInstanceOf(SQLException.class);
	}

	@Test
	void rejectsCrossOrderIdempotencyAndProviderIdentifierCollisions()
			throws Exception {
		UUID firstOrderId = UUID.fromString(
			create(UUID.randomUUID(), "1", 201).at("/order/id").asText());
		UUID secondOrderId = UUID.fromString(
			create(UUID.randomUUID(), "1", 201).at("/order/id").asText());
		UUID sharedKey = UUID.randomUUID();
		PaymentGateway fixedRejected = fixedGateway(
			"fake-shared-provider-id",
			PaymentResultStatus.REJECTED);

		try (var scope = tenantContext.open("tenant-a")) {
			paymentService(fixedRejected).initiate(
				new PaymentCommand(firstOrderId, sharedKey));
			assertThatThrownBy(() -> paymentService(fixedRejected).initiate(
				new PaymentCommand(secondOrderId, sharedKey)))
				.isInstanceOf(PaymentConflictException.class)
				.hasMessageContaining("otra operación");
			assertThatThrownBy(() -> paymentService(fixedRejected).initiate(
				new PaymentCommand(secondOrderId, UUID.randomUUID())))
				.isInstanceOf(PaymentConflictException.class)
				.hasMessageContaining("proveedor");
		}

		assertThat(count(TENANT_A_DATABASE,
			"SELECT COUNT(*) FROM payment_transactions")).isEqualTo(1);
		assertThat(count(TENANT_A_DATABASE, """
			SELECT COUNT(*) FROM payment_intents WHERE status = 'REQUIRES_REVIEW'
			""")).isEqualTo(1);
	}

	@Test
	void resolvesConcurrentCrossOrderIdempotencyAsADomainConflict()
			throws Exception {
		UUID firstOrderId = UUID.fromString(
			create(UUID.randomUUID(), "1", 201).at("/order/id").asText());
		UUID secondOrderId = UUID.fromString(
			create(UUID.randomUUID(), "1", 201).at("/order/id").asText());
		UUID sharedKey = UUID.randomUUID();
		PaymentApplicationService payments = paymentService(PaymentResultStatus.REJECTED);
		CountDownLatch start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			Future<Object> first = executor.submit(() -> {
				start.await();
				return capturePaymentResult(payments, firstOrderId, sharedKey);
			});
			Future<Object> second = executor.submit(() -> {
				start.await();
				return capturePaymentResult(payments, secondOrderId, sharedKey);
			});
			start.countDown();
			List<Object> results = List.of(
				first.get(10, TimeUnit.SECONDS),
				second.get(10, TimeUnit.SECONDS));

			assertThat(resultTypes(results)).containsExactlyInAnyOrder(
				PaymentInitiation.class.getName(),
				PaymentConflictException.class.getName());
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(count(TENANT_A_DATABASE,
			"SELECT COUNT(*) FROM payment_intents")).isEqualTo(1);
	}

	@Test
	void resolvesConcurrentProviderIdentifierCollisionAndReleasesLosingOrder()
			throws Exception {
		UUID firstOrderId = UUID.fromString(
			create(UUID.randomUUID(), "1", 201).at("/order/id").asText());
		UUID secondOrderId = UUID.fromString(
			create(UUID.randomUUID(), "1", 201).at("/order/id").asText());
		PaymentApplicationService payments = paymentService(
			new SharedIdentifierBarrierGateway());
		var executor = Executors.newFixedThreadPool(2);
		try {
			Future<Object> first = executor.submit(() -> capturePaymentResult(
				payments, firstOrderId, UUID.randomUUID()));
			Future<Object> second = executor.submit(() -> capturePaymentResult(
				payments, secondOrderId, UUID.randomUUID()));
			List<Object> results = List.of(
				first.get(10, TimeUnit.SECONDS),
				second.get(10, TimeUnit.SECONDS));

			assertThat(resultTypes(results)).containsExactlyInAnyOrder(
				PaymentInitiation.class.getName(),
				PaymentConflictException.class.getName());
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(count(TENANT_A_DATABASE,
			"SELECT COUNT(*) FROM payment_transactions")).isEqualTo(1);
		assertThat(count(TENANT_A_DATABASE, """
			SELECT COUNT(*) FROM payment_intents WHERE status = 'REQUIRES_REVIEW'
			""")).isEqualTo(1);
	}

	@Test
	void invalidGatewayCommercialDataRequiresReviewWithoutStockEffects()
			throws Exception {
		UUID orderId = UUID.fromString(
			create(UUID.randomUUID(), "1", 201).at("/order/id").asText());
		PaymentGateway mismatchedAmount = new PaymentGateway() {
			@Override
			public PaymentProvider provider() {
				return PaymentProvider.FAKE;
			}

			@Override
			public GatewayPayment createPayment(
					com.comercioflex.payment.application.GatewayPaymentRequest request) {
				return new GatewayPayment(
					"fake-invalid-amount",
					PaymentResultStatus.APPROVED,
					request.amount().add(java.math.BigDecimal.ONE),
					request.currencyCode());
			}
		};

		try (var scope = tenantContext.open("tenant-a")) {
			assertThatThrownBy(() -> paymentService(mismatchedAmount).initiate(
				new PaymentCommand(orderId, UUID.randomUUID())))
				.isInstanceOf(PaymentConflictException.class)
				.hasMessageContaining("no coincide");
		}

		assertThat(text(TENANT_A_DATABASE, "SELECT status FROM payment_intents"))
			.isEqualTo("REQUIRES_REVIEW");
		assertThat(count(TENANT_A_DATABASE,
			"SELECT COUNT(*) FROM payment_transactions")).isZero();
		assertThat(decimal(TENANT_A_DATABASE,
			"SELECT quantity FROM inventory_balances")).isEqualTo("5.000");
		assertThat(count(TENANT_A_DATABASE,
			"SELECT COUNT(*) FROM inventory_movements")).isZero();
	}

	@Test
	void verifiedLateApprovalConsumesAnActiveReservationAndRemainsTenantIsolated()
			throws Exception {
		UUID orderId = UUID.fromString(
			create(UUID.randomUUID(), "1", 201).at("/order/id").asText());
		PaymentGateway lateApproval = new PaymentGateway() {
			@Override
			public PaymentProvider provider() {
				return PaymentProvider.FAKE;
			}

			@Override
			public GatewayPayment createPayment(
					com.comercioflex.payment.application.GatewayPaymentRequest request) {
				try {
					execute(TENANT_A_DATABASE, """
						UPDATE orders
						SET reservation_expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND
						""");
				}
				catch (SQLException exception) {
					throw new IllegalStateException(exception);
				}
				return new GatewayPayment(
					"fake-late-approval",
					PaymentResultStatus.APPROVED,
					request.amount(),
					request.currencyCode());
			}
		};

		try (var scope = tenantContext.open("tenant-a")) {
			var result = paymentService(lateApproval)
				.initiate(new PaymentCommand(orderId, UUID.randomUUID()));
			assertThat(result.paymentIntent().status())
				.isEqualTo(PaymentIntentStatus.APPROVED);
		}
		try (var scope = tenantContext.open("tenant-b")) {
			assertThatThrownBy(() -> paymentService(PaymentResultStatus.APPROVED)
				.initiate(new PaymentCommand(orderId, UUID.randomUUID())))
				.isInstanceOf(com.comercioflex.payment.application.InvalidPaymentException.class);
		}

		assertThat(text(TENANT_A_DATABASE, "SELECT status FROM orders"))
			.isEqualTo("CONFIRMED");
		assertThat(count(TENANT_A_DATABASE, """
			SELECT COUNT(*) FROM payment_transactions WHERE review_required = TRUE
			""")).isZero();
		assertThat(text(TENANT_A_DATABASE,
			"SELECT status FROM inventory_reservations")).isEqualTo("CONSUMED");
		assertThat(count(TENANT_B_DATABASE,
			"SELECT COUNT(*) FROM payment_intents")).isZero();
		assertThat(decimal(TENANT_A_DATABASE,
			"SELECT quantity FROM inventory_balances")).isEqualTo("4.000");
	}

	@RepeatedTest(3)
	void serializesDuplicateApprovedPaymentsWithoutDuplicatingStockEffects()
			throws Exception {
		UUID orderId = UUID.fromString(
			create(UUID.randomUUID(), "2", 201).at("/order/id").asText());
		UUID key = UUID.randomUUID();
		BlockingCountingGateway gateway = new BlockingCountingGateway();
		PaymentApplicationService payments = paymentService(gateway);
		var executor = Executors.newFixedThreadPool(2);
		try {
			Future<PaymentInitiation> first = executor.submit(
				() -> initiateWithinTenant(payments, orderId, key));
			assertThat(gateway.awaitInvocation()).isTrue();
			Future<PaymentInitiation> second = executor.submit(
				() -> initiateWithinTenant(payments, orderId, key));
			PaymentInitiation replay = second.get(10, TimeUnit.SECONDS);
			assertThat(replay.replayed()).isTrue();
			gateway.release();
			assertThat(first.get().paymentIntent().id())
				.isEqualTo(replay.paymentIntent().id());
		}
		finally {
			gateway.release();
			executor.shutdownNow();
		}

		assertThat(gateway.invocations()).isEqualTo(1);
		assertThat(count(TENANT_A_DATABASE,
			"SELECT COUNT(*) FROM payment_intents")).isEqualTo(1);
		assertThat(count(TENANT_A_DATABASE,
			"SELECT COUNT(*) FROM payment_transactions")).isEqualTo(1);
		assertThat(count(TENANT_A_DATABASE,
			"SELECT COUNT(*) FROM inventory_movements")).isEqualTo(1);
		assertThat(count(TENANT_A_DATABASE, """
			SELECT COUNT(*) FROM transactional_email_outbox
			WHERE event_type = 'ORDER_CONFIRMED'
			""")).isEqualTo(1);
		assertThat(count(TENANT_B_DATABASE,
			"SELECT COUNT(*) FROM transactional_email_outbox")).isZero();
		assertThat(decimal(TENANT_A_DATABASE,
			"SELECT quantity FROM inventory_balances")).isEqualTo("3.000");
	}

	@Test
	void securesAndOperatesAdminOrderEndpoints() throws Exception {
		JsonNode created = create(UUID.randomUUID(), "1", 201);
		String orderId = created.at("/order/id").asText();
		var operator = authentication(new UsernamePasswordAuthenticationToken(
			operatorPrincipal(),
			"test",
			operatorPrincipal().getAuthorities()));

		mockMvc.perform(get("/api/v1/stores/tienda-a/admin/orders"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/stores/tienda-a/admin/orders").with(operator))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(orderId));
		mockMvc.perform(post("/api/v1/stores/tienda-a/admin/orders/" + orderId + "/transitions")
				.with(operator)
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"targetStatus\":\"CONFIRMED\"}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/stores/tienda-a/admin/orders/" + orderId + "/transitions")
				.with(operator)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"targetStatus\":\"CONFIRMED\"}"))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/stores/tienda-a/admin/orders/" + orderId + "/transitions")
				.with(operator)
				.with(csrf())
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(
					"{\"targetStatus\":\"CONFIRMED\",\"note\":\"Stock revisado\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CONFIRMED"))
			.andExpect(jsonPath("$.history[1].actorDisplayName").value("Operador"));
		mockMvc.perform(get("/api/v1/stores/tienda-b/admin/orders").with(operator))
			.andExpect(status().isForbidden());
	}

	@Test
	void appliesBankTransferDiscountAndKeepsMercadoPagoAtListPrice() throws Exception {
		execute(TENANT_A_DATABASE, """
			UPDATE store_settings
			SET bank_transfer_enabled = TRUE,
				bank_transfer_discount_percentage = 20.00,
				bank_account_holder = 'Tienda A',
				bank_alias = 'TIENDA.A'
			""");

		JsonNode mercadoPagoCreated = create(
			UUID.randomUUID(),
			"1",
			201);

		UUID mercadoPagoOrderId = UUID.fromString(
			mercadoPagoCreated.at("/order/id").asText());

		// API - Mercado Pago
		assertThat(mercadoPagoCreated.at("/order/paymentMethod").asText())
			.isEqualTo("MERCADO_PAGO");

		assertThat(mercadoPagoCreated.at("/order/listSubtotal").asText())
			.isEqualTo("2500.00");

		assertThat(mercadoPagoCreated.at("/order/discountPercentage").asText())
			.isEqualTo("0.00");

		assertThat(mercadoPagoCreated.at("/order/discountAmount").asText())
			.isEqualTo("0.00");

		assertThat(mercadoPagoCreated.at("/order/subtotal").asText())
			.isEqualTo("2500.00");

		// Crear pedido por transferencia sin usar body(String, String)
		MockHttpServletResponse bankTransferResponse = mockMvc.perform(
				post(orders("tienda-a"))
					.with(csrf())
					.header("Idempotency-Key", UUID.randomUUID())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{
							"customerName": "Ana Pérez",
							"customerPhone": "11 5555 1234",
							"customerEmail": "ana@example.com",
							"notes": "Pedido con descuento",
							"paymentMethod": "BANK_TRANSFER",
							"items": [
								{
									"variantId": "%s",
									"quantity": "1"
								}
							]
						}
						""".formatted(VARIANT_A)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse();

		JsonNode bankTransferCreated =
			objectMapper.readTree(bankTransferResponse.getContentAsString());

		UUID bankTransferOrderId = UUID.fromString(
			bankTransferCreated.at("/order/id").asText());

		// API - Transferencia
		assertThat(bankTransferCreated.at("/order/paymentMethod").asText())
			.isEqualTo("BANK_TRANSFER");

		assertThat(bankTransferCreated.at("/order/listSubtotal").asText())
			.isEqualTo("2500.00");

		assertThat(bankTransferCreated.at("/order/discountPercentage").asText())
			.isEqualTo("20.00");

		assertThat(bankTransferCreated.at("/order/discountAmount").asText())
			.isEqualTo("500.00");

		assertThat(bankTransferCreated.at("/order/subtotal").asText())
			.isEqualTo("2000.00");

		// Base de datos - Mercado Pago
		assertThat(text(
			TENANT_A_DATABASE,
			"""
			SELECT payment_method
			FROM orders
			WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(mercadoPagoOrderId)))
			.isEqualTo("MERCADO_PAGO");

		assertThat(decimal(
			TENANT_A_DATABASE,
			"""
			SELECT list_subtotal
			FROM orders
			WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(mercadoPagoOrderId)))
			.isEqualTo("2500.00");

		assertThat(decimal(
			TENANT_A_DATABASE,
			"""
			SELECT discount_percentage
			FROM orders
			WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(mercadoPagoOrderId)))
			.isEqualTo("0.00");

		assertThat(decimal(
			TENANT_A_DATABASE,
			"""
			SELECT discount_amount
			FROM orders
			WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(mercadoPagoOrderId)))
			.isEqualTo("0.00");

		assertThat(decimal(
			TENANT_A_DATABASE,
			"""
			SELECT subtotal
			FROM orders
			WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(mercadoPagoOrderId)))
			.isEqualTo("2500.00");

		// Base de datos - Transferencia
		assertThat(text(
			TENANT_A_DATABASE,
			"""
			SELECT payment_method
			FROM orders
			WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(bankTransferOrderId)))
			.isEqualTo("BANK_TRANSFER");

		assertThat(decimal(
			TENANT_A_DATABASE,
			"""
			SELECT list_subtotal
			FROM orders
			WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(bankTransferOrderId)))
			.isEqualTo("2500.00");

		assertThat(decimal(
			TENANT_A_DATABASE,
			"""
			SELECT discount_percentage
			FROM orders
			WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(bankTransferOrderId)))
			.isEqualTo("20.00");

		assertThat(decimal(
			TENANT_A_DATABASE,
			"""
			SELECT discount_amount
			FROM orders
			WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(bankTransferOrderId)))
			.isEqualTo("500.00");

		assertThat(decimal(
			TENANT_A_DATABASE,
			"""
			SELECT subtotal
			FROM orders
			WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(bankTransferOrderId)))
			.isEqualTo("2000.00");
	}
	@Test
	void rejectsBankTransferOrderWhenBankTransferIsDisabled() throws Exception {
		mockMvc.perform(post(orders("tienda-a"))
				.with(csrf())
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"customerName": "Ana Pérez",
						"customerPhone": "11 5555 1234",
						"customerEmail": "ana@example.com",
						"notes": "Pedido por transferencia",
						"paymentMethod": "BANK_TRANSFER",
						"items": [
							{
								"variantId": "%s",
								"quantity": "1"
							}
						]
					}
					""".formatted(VARIANT_A)))
			.andExpect(status().isBadRequest());

		assertThat(count(
				TENANT_A_DATABASE,
				"SELECT COUNT(*) FROM orders"))
			.isZero();
	}

	@Test
	void roundsBankTransferDiscountToTwoDecimals() throws Exception {
		execute(TENANT_A_DATABASE, """
			UPDATE product_variants
			SET price = 999.99
			WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(VARIANT_A));

		execute(TENANT_A_DATABASE, """
			UPDATE store_settings
			SET bank_transfer_enabled = TRUE,
				bank_transfer_discount_percentage = 15.00,
				bank_account_holder = 'Tienda A',
				bank_alias = 'TIENDA.A'
			""");

		MockHttpServletResponse response = mockMvc.perform(
				post(orders("tienda-a"))
					.with(csrf())
					.header("Idempotency-Key", UUID.randomUUID())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{
							"customerName": "Ana Pérez",
							"customerPhone": "11 5555 1234",
							"customerEmail": "ana@example.com",
							"notes": "Prueba de redondeo",
							"paymentMethod": "BANK_TRANSFER",
							"items": [
								{
									"variantId": "%s",
									"quantity": "1"
								}
							]
						}
						""".formatted(VARIANT_A)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse();

		JsonNode created = objectMapper.readTree(response.getContentAsString());

		UUID orderId = UUID.fromString(
			created.at("/order/id").asText());

		assertThat(decimal(
			TENANT_A_DATABASE,
			"""
			SELECT list_subtotal
			FROM orders
			WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(orderId)))
			.isEqualTo("999.99");

		assertThat(decimal(
			TENANT_A_DATABASE,
			"""
			SELECT discount_percentage
			FROM orders
			WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(orderId)))
			.isEqualTo("15.00");

		assertThat(decimal(
			TENANT_A_DATABASE,
			"""
			SELECT discount_amount
			FROM orders
			WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(orderId)))
			.isEqualTo("150.00");

		assertThat(decimal(
			TENANT_A_DATABASE,
			"""
			SELECT subtotal
			FROM orders
			WHERE public_id = UUID_TO_BIN('%s')
			""".formatted(orderId)))
			.isEqualTo("849.99");
	}

	@Test
	void allowsPublicOrderWithoutCsrfAndValidatesPayloadAndTenantScope() throws Exception {
		mockMvc.perform(post(orders("tienda-a"))
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("1")))
			.andExpect(status().isCreated());
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

	@Test
	void requiresCustomerNameAndEmail() throws Exception {
		mockMvc.perform(post(orders("tienda-a"))
				.with(csrf())
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"customerPhone": "11 5555 1234",
						"customerEmail": "ana@example.com",
						"items": [{"variantId": "%s", "quantity": "1"}]
					}
					""".formatted(VARIANT_A)))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post(orders("tienda-a"))
				.with(csrf())
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"customerName": "Ana Pérez",
						"customerPhone": "11 5555 1234",
						"customerEmail": "   ",
						"items": [{"variantId": "%s", "quantity": "1"}]
					}
					""".formatted(VARIANT_A)))
			.andExpect(status().isBadRequest());

		assertThat(count(TENANT_A_DATABASE, "SELECT COUNT(*) FROM orders")).isZero();
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
							"paymentMethod": "MERCADO_PAGO",
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

	private PlatformPrincipal operatorPrincipal() {
		return new PlatformPrincipal(new UserCredentials(
			9001L,
			OPERATOR_ID,
			"operator@example.test",
			"Operador",
			"{noop}test",
			UserStatus.ACTIVE));
	}

	private PaymentApplicationService paymentService(PaymentResultStatus result) {
		return paymentService(new FakePaymentGateway(result));
	}

	private PaymentApplicationService paymentService(PaymentGateway gateway) {
		return new PaymentApplicationService(
			paymentRepository,
			gateway,
			paidOrderConfirmer,
			tenantTransactionTemplate);
	}

	private PaymentGateway fixedGateway(
			String providerPaymentId,
			PaymentResultStatus status) {
		return new PaymentGateway() {
			@Override
			public PaymentProvider provider() {
				return PaymentProvider.FAKE;
			}

			@Override
			public GatewayPayment createPayment(
					com.comercioflex.payment.application.GatewayPaymentRequest request) {
				return new GatewayPayment(
					providerPaymentId,
					status,
					request.amount(),
					request.currencyCode());
			}
		};
	}

	private PaymentInitiation initiateWithinTenant(
			PaymentApplicationService payments,
			UUID orderId,
			UUID key) {
		try (var scope = tenantContext.open("tenant-a")) {
			return payments.initiate(new PaymentCommand(orderId, key));
		}
	}

	private Object capturePaymentResult(
			PaymentApplicationService payments,
			UUID orderId,
			UUID key) {
		try {
			return initiateWithinTenant(payments, orderId, key);
		}
		catch (RuntimeException exception) {
			return exception;
		}
	}

	private List<String> resultTypes(List<Object> results) {
		return results.stream()
			.map(result -> result.getClass().getName())
			.toList();
	}

	private static final class SharedIdentifierBarrierGateway
			implements PaymentGateway {

		private final CountDownLatch invocations = new CountDownLatch(2);

		@Override
		public PaymentProvider provider() {
			return PaymentProvider.FAKE;
		}

		@Override
		public GatewayPayment createPayment(
				com.comercioflex.payment.application.GatewayPaymentRequest request) {
			invocations.countDown();
			try {
				if (!invocations.await(10, TimeUnit.SECONDS)) {
					throw new IllegalStateException("No llegaron ambas solicitudes.");
				}
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(exception);
			}
			return new GatewayPayment(
				"fake-concurrent-shared-id",
				PaymentResultStatus.REJECTED,
				request.amount(),
				request.currencyCode());
		}
	}

	private byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static void resetTenant(
			MySQLContainer<?> database,
			String storeName) throws SQLException {
		execute(database, "DELETE FROM payment_transactions");
		execute(database, "DELETE FROM payment_intents");
		execute(database, "DELETE FROM order_status_history");
		execute(database, "DELETE FROM inventory_movements");
		execute(database, "DELETE FROM inventory_reservations");
		execute(database, "DELETE FROM order_items");
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

	private static final class BlockingCountingGateway implements PaymentGateway {

		private final AtomicInteger invocations = new AtomicInteger();
		private final CountDownLatch invoked = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);

		@Override
		public PaymentProvider provider() {
			return PaymentProvider.FAKE;
		}

		@Override
		public GatewayPayment createPayment(
				com.comercioflex.payment.application.GatewayPaymentRequest request) {
			invocations.incrementAndGet();
			invoked.countDown();
			try {
				if (!release.await(10, TimeUnit.SECONDS)) {
					throw new IllegalStateException("La prueba no liberó el gateway falso.");
				}
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(exception);
			}
			return new GatewayPayment(
				"fake-blocking-payment",
				PaymentResultStatus.APPROVED,
				request.amount(),
				request.currencyCode());
		}

		boolean awaitInvocation() throws InterruptedException {
			return invoked.await(10, TimeUnit.SECONDS);
		}

		void release() {
			release.countDown();
		}

		int invocations() {
			return invocations.get();
		}
	}
}