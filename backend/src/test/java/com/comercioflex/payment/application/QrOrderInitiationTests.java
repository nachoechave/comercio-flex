package com.comercioflex.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.order.application.AdminOrderRepository;
import com.comercioflex.order.application.PaidOrderConfirmer;
import com.comercioflex.order.domain.OrderStatus;
import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.payment.domain.PaymentIntentStatus;
import com.comercioflex.tenant.application.ResolvedTenant;
import com.comercioflex.tenant.application.TenantResolver;

class QrOrderInitiationTests {

	private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
	private static final UUID ORDER_ID =
		UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final UUID CLIENT_KEY =
		UUID.fromString("22222222-2222-4222-8222-222222222222");
	private static final String LOOKUP_TOKEN = "A".repeat(43);
	private final QrOrderRepository repository = mock(QrOrderRepository.class);
	private final QrOrderControlRepository routes = mock(QrOrderControlRepository.class);
	private final CheckoutControlRepository capabilities = mock(CheckoutControlRepository.class);
	private final QrSetupRepository setups = mock(QrSetupRepository.class);
	private final PaymentCredentialResolver credentials = mock(PaymentCredentialResolver.class);
	private final MercadoPagoQrOrderGateway gateway = mock(MercadoPagoQrOrderGateway.class);
	private final TenantResolver tenants = mock(TenantResolver.class);
	private QrOrderService service;

	@BeforeEach
	void setUp() {
		service = new QrOrderService(
			repository, mock(CheckoutRepository.class), routes, capabilities, setups,
			credentials, gateway, mock(PaidOrderConfirmer.class),
			mock(AdminOrderRepository.class), tenants, oauthProperties(),
			checkoutProperties(), transactions(), transactions(),
			Clock.fixed(NOW, ZoneOffset.UTC));
		when(tenants.resolveActive("tienda-a"))
			.thenReturn(new ResolvedTenant(1L, "tienda-a", "Tienda A", "tenant-a"));
		when(credentials.resolve(1L, "tienda-a")).thenReturn(credential());
		when(setups.find(1L, PaymentEnvironment.PRODUCTION))
			.thenReturn(Optional.of(readySetup()));
		when(repository.lockOrder(any(), any())).thenReturn(Optional.of(order()));
		when(repository.findCurrentByOrder(any(), any())).thenReturn(Optional.empty());
		when(repository.hasBlockingIntent(20L)).thenReturn(false);
		when(repository.nextAttemptNumber(20L)).thenReturn(1);
	}

	@Test
	void persistsAStableProviderKeyBeforeCreatingTheDynamicQrOrder() {
		AtomicReference<StoredQrOrderAttempt> stored = new AtomicReference<>();
		org.mockito.Mockito.doAnswer(invocation -> {
			stored.set(attempt(
				invocation.getArgument(0), invocation.getArgument(2),
				invocation.getArgument(3), invocation.getArgument(4),
				invocation.getArgument(8), invocation.getArgument(9),
				invocation.getArgument(10), null, "CREATING", PaymentIntentStatus.CREATED));
			return null;
		}).when(repository).insert(
			any(), any(Long.class), any(), any(), any(), any(Integer.class), any(), any(),
			any(), any(), any(), any(), any(), any(), any());
		when(repository.findByPublicId(any(), anyBoolean()))
			.thenAnswer(invocation -> Optional.ofNullable(stored.get()));
		when(gateway.createOrder(any(), any())).thenAnswer(invocation -> {
			CreateQrOrderCommand command = invocation.getArgument(1);
			return provider(command, "PROVIDER_ORDER");
		});
		org.mockito.Mockito.doAnswer(invocation -> {
			StoredQrOrderAttempt current = stored.get();
			stored.set(attempt(
				current.id(), current.idempotencyKey(), current.requestFingerprint(),
				current.transitionIdempotencyKey(), current.externalReference(),
				current.providerIdempotencyKey(), current.providerExpiresAt(),
				"PROVIDER_ORDER", "READY", PaymentIntentStatus.PENDING));
			return null;
		}).when(repository).attachProviderOrder(any(), any(), any(), any(), any(), any());

		QrOrderInitiation result = service.initiate(
			"tienda-a", ORDER_ID, LOOKUP_TOKEN, CLIENT_KEY);

		assertThat(result.qrData()).isEqualTo("provider-qr-data");
		assertThat(result.status()).isEqualTo(PaymentIntentStatus.PENDING);
		var order = inOrder(repository, gateway);
		order.verify(repository).insert(
			any(), any(Long.class), any(), any(), any(), any(Integer.class), any(), any(),
			any(), any(), any(), any(), any(), any(), any());
		order.verify(gateway).createOrder(any(), any());
		ArgumentCaptor<CreateQrOrderCommand> command =
			ArgumentCaptor.forClass(CreateQrOrderCommand.class);
		verify(gateway).createOrder(any(), command.capture());
		assertThat(command.getValue().providerIdempotencyKey())
			.isEqualTo(stored.get().providerIdempotencyKey());
		assertThat(command.getValue().externalPosId()).isEqualTo("CFP_POS");
		assertThat(command.getValue().expiration()).isEqualTo(Duration.ofMinutes(29).plusSeconds(55));
	}

	@Test
	void retryAfterTimeoutUsesTheSamePersistedProviderIdempotencyKey() {
		UUID providerKey = UUID.fromString("33333333-3333-4333-8333-333333333333");
		AtomicReference<StoredQrOrderAttempt> stored = new AtomicReference<>(attempt(
			UUID.randomUUID(), CLIENT_KEY, requestFingerprint(), UUID.randomUUID(),
			"cf_qr_reference", providerKey, NOW.plusSeconds(1_795), null,
			"FAILED", PaymentIntentStatus.CREATED));
		when(repository.findByIdempotencyKey(CLIENT_KEY)).thenReturn(Optional.of(stored.get()));
		when(repository.claimCreation(any(), any(), any())).thenReturn(true);
		when(repository.findByPublicId(any(), anyBoolean()))
			.thenAnswer(invocation -> Optional.of(stored.get()));
		when(gateway.createOrder(any(), any())).thenAnswer(invocation ->
			provider(invocation.getArgument(1), "PROVIDER_ORDER"));
		org.mockito.Mockito.doAnswer(invocation -> {
			StoredQrOrderAttempt current = stored.get();
			stored.set(attempt(
				current.id(), current.idempotencyKey(), current.requestFingerprint(),
				current.transitionIdempotencyKey(), current.externalReference(),
				current.providerIdempotencyKey(), current.providerExpiresAt(),
				"PROVIDER_ORDER", "READY", PaymentIntentStatus.PENDING));
			return null;
		}).when(repository).attachProviderOrder(any(), any(), any(), any(), any(), any());

		service.initiate("tienda-a", ORDER_ID, LOOKUP_TOKEN, CLIENT_KEY);

		ArgumentCaptor<CreateQrOrderCommand> command =
			ArgumentCaptor.forClass(CreateQrOrderCommand.class);
		verify(gateway).createOrder(any(), command.capture());
		assertThat(command.getValue().providerIdempotencyKey()).isEqualTo(providerKey);
		verify(repository, never()).insert(
			any(), any(Long.class), any(), any(), any(), any(Integer.class), any(), any(),
			any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void concurrentCreateDoesNotInvokeTheProviderTwice() {
		StoredQrOrderAttempt existing = attempt(
			UUID.randomUUID(), CLIENT_KEY, requestFingerprint(), UUID.randomUUID(),
			"cf_qr_reference", UUID.randomUUID(), NOW.plusSeconds(1_795), null,
			"CREATING", PaymentIntentStatus.CREATED);
		when(repository.findByIdempotencyKey(CLIENT_KEY)).thenReturn(Optional.of(existing));
		when(repository.claimCreation(any(), any(), any())).thenReturn(false);

		assertThatThrownBy(() ->
			service.initiate("tienda-a", ORDER_ID, LOOKUP_TOKEN, CLIENT_KEY))
			.isInstanceOfSatisfying(QrOrderException.class, exception -> {
				assertThat(exception.code()).isEqualTo("QR_CREATION_IN_PROGRESS");
				assertThat(exception.retryable()).isTrue();
			});

		verify(gateway, never()).createOrder(any(), any());
	}

	@Test
	void reloadReturnsTheStoredQrWithoutCreatingAnotherProviderOrder() {
		StoredQrOrderAttempt ready = attempt(
			UUID.randomUUID(), CLIENT_KEY, new byte[32], UUID.randomUUID(),
			"cf_qr_reference", UUID.randomUUID(), NOW.plusSeconds(1_795),
			"PROVIDER_ORDER", "READY", PaymentIntentStatus.PENDING);
		when(repository.findCurrentByOrder(any(), any())).thenReturn(Optional.of(ready));

		QrOrderInitiation result = service.findCurrent(
			"tienda-a", ORDER_ID, LOOKUP_TOKEN).orElseThrow();

		assertThat(result.qrData()).isEqualTo("provider-qr-data");
		assertThat(result.replayed()).isTrue();
		verify(gateway, never()).createOrder(any(), any());
	}

	@Test
	void missingQrSetupFailsClosedBeforeAnyProviderCall() {
		when(setups.find(1L, PaymentEnvironment.PRODUCTION)).thenReturn(Optional.empty());

		assertThatThrownBy(() ->
			service.initiate("tienda-a", ORDER_ID, LOOKUP_TOKEN, CLIENT_KEY))
			.isInstanceOfSatisfying(QrOrderException.class, exception ->
				assertThat(exception.code()).isEqualTo("QR_SETUP_NOT_READY"));

		verify(gateway, never()).createOrder(any(), any());
		verify(repository, never()).lockOrder(any(), any());
	}

	private StoredQrOrderAttempt attempt(
			UUID id, UUID idempotencyKey, byte[] fingerprint, UUID transitionKey,
			String externalReference, UUID providerKey, Instant providerExpiresAt,
			String providerOrderId, String creationStatus, PaymentIntentStatus status) {
		return new StoredQrOrderAttempt(
			10L, id, 20L, ORDER_ID, 23L, "PENDING_CONFIRMATION",
			NOW.plus(Duration.ofMinutes(30)), idempotencyKey, fingerprint, transitionKey,
			status, 1, new BigDecimal("1250.00"), "ARS", externalReference, 0L,
			30L, providerKey, providerOrderId,
			providerOrderId == null ? null : "provider-qr-data", "created",
			providerExpiresAt, "123456", PaymentEnvironment.PRODUCTION, "CFP_POS",
			creationStatus, NOW, 0L, NOW);
	}

	private ProviderQrOrder provider(CreateQrOrderCommand command, String orderId) {
		return new ProviderQrOrder(
			orderId, "qr", "created", "created", command.externalReference(),
			command.amount(), "ARS", "123456", true, command.externalPosId(),
			"provider-qr-data", null, null, null, NOW);
	}

	private CheckoutOrder order() {
		return new CheckoutOrder(
			20L, ORDER_ID, OrderStatus.PENDING_CONFIRMATION,
			new BigDecimal("1250.00"), "ARS", NOW.plus(Duration.ofMinutes(30)));
	}

	private StoredQrSetup readySetup() {
		return new StoredQrSetup(
			1L, 1L, PaymentEnvironment.PRODUCTION, "STORE_PROVIDER", "CFS_STORE",
			"POS_PROVIDER", "CFP_POS", QrProvisioningStatus.LISTO,
			QrAuthorizationStatus.AUTHORIZED, UUID.randomUUID(), 0L);
	}

	private PaymentCredential credential() {
		return new PaymentCredential(
			"oauth-token-fixture", "123456", PaymentEnvironment.PRODUCTION,
			PaymentCredential.Source.TENANT_OAUTH);
	}

	private byte[] requestFingerprint() {
		try {
			return MessageDigest.getInstance("SHA-256").digest(
				("mercado-pago-qr:v1:" + ORDER_ID).getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}

	private PaymentOAuthProperties oauthProperties() {
		return new PaymentOAuthProperties(
			true, PaymentEnvironment.PRODUCTION, "client", "secret",
			URI.create("https://example.test/callback"), URI.create("https://auth.test"),
			URI.create("https://api.test"), URI.create("https://identity.test"),
			URI.create("https://frontend.test"), Duration.ofSeconds(3),
			Duration.ofSeconds(8), "v1", "fixture-key");
	}

	private CheckoutProProperties checkoutProperties() {
		return new CheckoutProProperties(
			true, null, null, null, URI.create("https://backend.test"),
			URI.create("https://frontend.test"), "webhook-secret-fixture",
			Duration.ofSeconds(3), Duration.ofSeconds(8), Duration.ofSeconds(30),
			Duration.ofSeconds(30), 8, Duration.ofHours(24));
	}

	@SuppressWarnings("unchecked")
	private static TransactionTemplate transactions() {
		TransactionTemplate template = mock(TransactionTemplate.class);
		when(template.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<Object> callback = invocation.getArgument(0);
			return callback.doInTransaction(mock(TransactionStatus.class));
		});
		return template;
	}
}
