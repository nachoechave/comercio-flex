package com.comercioflex.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.order.application.AdminOrderRepository;
import com.comercioflex.order.application.OrderTransitionExecution;
import com.comercioflex.order.application.PaidOrderConfirmer;
import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.payment.domain.PaymentIntentStatus;
import com.comercioflex.tenant.application.TenantResolver;

class QrOrderServiceTests {

	private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
	private final QrOrderRepository repository = mock(QrOrderRepository.class);
	private final CheckoutRepository paymentTransactions = mock(CheckoutRepository.class);
	private final PaidOrderConfirmer confirmer = mock(PaidOrderConfirmer.class);
	private final TransactionTemplate tenantTransactions = transactions();
	private QrOrderService service;

	@BeforeEach
	void setUp() {
		service = new QrOrderService(
			repository, paymentTransactions, mock(QrOrderControlRepository.class),
			mock(CheckoutControlRepository.class), mock(QrSetupRepository.class),
			mock(PaymentCredentialResolver.class), mock(MercadoPagoQrOrderGateway.class),
			confirmer, mock(AdminOrderRepository.class), mock(TenantResolver.class),
			oauthProperties(), checkoutProperties(), tenantTransactions, transactions(),
			Clock.fixed(NOW, ZoneOffset.UTC));
		when(repository.findByPublicId(attempt().id(), true))
			.thenReturn(Optional.of(attempt()));
	}

	@Test
	void appliesAnAccreditedQrPaymentThroughTheExistingPaidOrderConfirmerExactlyOnce() {
		when(confirmer.confirmWithinCurrentTransaction(any(), any(), any()))
			.thenReturn(new OrderTransitionExecution(null, false));

		QrOrderProcessingResult result = service.applyProviderOrder(
			route(), credential(), provider("processed", "approved", "cf_qr_reference"));

		assertThat(result).isEqualTo(QrOrderProcessingResult.APPROVED);
		verify(confirmer).confirmWithinCurrentTransaction(
			attempt().orderId(), attempt().transitionIdempotencyKey(), "Mercado Pago QR");
		ArgumentCaptor<VerifiedProviderPayment> payment =
			ArgumentCaptor.forClass(VerifiedProviderPayment.class);
		verify(paymentTransactions).applyVerifiedPayment(
			any(), payment.capture(), org.mockito.ArgumentMatchers.eq(true),
			org.mockito.ArgumentMatchers.eq(false), org.mockito.ArgumentMatchers.eq(NOW));
		assertThat(payment.getValue().providerPaymentId()).isEqualTo("PAYMENT_FIXTURE");
	}

	@Test
	void rejectsAnotherExternalReferenceBeforeApplyingStockOrEmailEffects() {
		assertThatThrownBy(() -> service.applyProviderOrder(
			route(), credential(), provider("processed", "approved", "another_reference")))
			.isInstanceOfSatisfying(QrOrderException.class, exception ->
				assertThat(exception.code()).isEqualTo("QR_PROVIDER_VALIDATION_FAILED"));

		verify(confirmer, never()).confirmWithinCurrentTransaction(any(), any(), any());
		verify(paymentTransactions, never()).applyVerifiedPayment(
			any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
			org.mockito.ArgumentMatchers.anyBoolean(), any());
	}

	@Test
	void mapsProviderExpirationWithoutConfirmingTheOrder() {
		QrOrderProcessingResult result = service.applyProviderOrder(
			route(), credential(), provider("expired", null, "cf_qr_reference"));

		assertThat(result).isEqualTo(QrOrderProcessingResult.EXPIRED);
		verify(repository).updateIntentStatus(
			any(), org.mockito.ArgumentMatchers.eq(PaymentIntentStatus.EXPIRED),
			org.mockito.ArgumentMatchers.eq(NOW));
		verify(confirmer, never()).confirmWithinCurrentTransaction(any(), any(), any());
	}

	@Test
	void keepsACreatedProviderOrderPendingWithoutApplyingPaymentEffects() {
		QrOrderProcessingResult result = service.applyProviderOrder(
			route(), credential(), provider("created", null, "cf_qr_reference"));

		assertThat(result).isEqualTo(QrOrderProcessingResult.PENDING);
		verify(confirmer, never()).confirmWithinCurrentTransaction(any(), any(), any());
		verify(paymentTransactions, never()).applyVerifiedPayment(
			any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
			org.mockito.ArgumentMatchers.anyBoolean(), any());
	}

	@Test
	void mapsProviderCancellationWithoutConfirmingTheOrder() {
		QrOrderProcessingResult result = service.applyProviderOrder(
			route(), credential(), provider("canceled", null, "cf_qr_reference"));

		assertThat(result).isEqualTo(QrOrderProcessingResult.CANCELED);
		verify(repository).updateIntentStatus(
			any(), org.mockito.ArgumentMatchers.eq(PaymentIntentStatus.REJECTED),
			org.mockito.ArgumentMatchers.eq(NOW));
		verify(confirmer, never()).confirmWithinCurrentTransaction(any(), any(), any());
	}

	@ParameterizedTest
	@MethodSource("invalidProviderOrders")
	void rejectsProviderOrdersThatDoNotMatchTheExpectedQrAttempt(ProviderQrOrder provider) {
		assertThatThrownBy(() -> service.applyProviderOrder(route(), credential(), provider))
			.isInstanceOfSatisfying(QrOrderException.class, exception ->
				assertThat(exception.code()).isEqualTo("QR_PROVIDER_VALIDATION_FAILED"));

		verify(confirmer, never()).confirmWithinCurrentTransaction(any(), any(), any());
		verify(paymentTransactions, never()).applyVerifiedPayment(
			any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
			org.mockito.ArgumentMatchers.anyBoolean(), any());
	}

	private static java.util.stream.Stream<Arguments> invalidProviderOrders() {
		return java.util.stream.Stream.of(
			Arguments.of(new ProviderQrOrder(
				"ANOTHER_ORDER", "qr", "processed", "processed", "cf_qr_reference",
				new BigDecimal("1250.00"), "ARS", "123456", true, "CFP_OPAQUE", null,
				"PAYMENT_FIXTURE", "approved", new BigDecimal("1250.00"), NOW)),
			Arguments.of(new ProviderQrOrder(
				"ORDER_FIXTURE", "qr", "processed", "processed", "cf_qr_reference",
				new BigDecimal("1250.00"), "ARS", "another-seller", true, "CFP_OPAQUE", null,
				"PAYMENT_FIXTURE", "approved", new BigDecimal("1250.00"), NOW)),
			Arguments.of(new ProviderQrOrder(
				"ORDER_FIXTURE", "qr", "processed", "processed", "cf_qr_reference",
				new BigDecimal("1251.00"), "ARS", "123456", true, "CFP_OPAQUE", null,
				"PAYMENT_FIXTURE", "approved", new BigDecimal("1251.00"), NOW)),
			Arguments.of(new ProviderQrOrder(
				"ORDER_FIXTURE", "qr", "processed", "processed", "cf_qr_reference",
				new BigDecimal("1250.00"), "USD", "123456", true, "CFP_OPAQUE", null,
				"PAYMENT_FIXTURE", "approved", new BigDecimal("1250.00"), NOW)));
	}

	private StoredQrOrderAttempt attempt() {
		return new StoredQrOrderAttempt(
			10L, UUID.fromString("11111111-1111-4111-8111-111111111111"), 20L,
			UUID.fromString("22222222-2222-4222-8222-222222222222"), 23L,
			"PENDING_CONFIRMATION", NOW.plus(Duration.ofMinutes(30)),
			UUID.fromString("33333333-3333-4333-8333-333333333333"), new byte[32],
			UUID.fromString("44444444-4444-4444-8444-444444444444"),
			PaymentIntentStatus.PENDING, 1, new BigDecimal("1250.00"), "ARS",
			"cf_qr_reference", 0L, 30L,
			UUID.fromString("55555555-5555-4555-8555-555555555555"),
			"ORDER_FIXTURE", "provider-qr-data", "created", NOW.plus(Duration.ofMinutes(29)),
			"123456", PaymentEnvironment.PRODUCTION, "CFP_OPAQUE", "READY", NOW, 0L, NOW);
	}

	private QrOrderRoute route() {
		return new QrOrderRoute(
			5L, 1L, "tienda-a", "tenant-a", PaymentEnvironment.PRODUCTION,
			attempt().id(), "ORDER_FIXTURE", "123456", "ACTIVE", 1,
			NOW.plus(Duration.ofMinutes(29)));
	}

	private ProviderQrOrder provider(String status, String paymentStatus, String reference) {
		return new ProviderQrOrder(
			"ORDER_FIXTURE", "qr", status, status, reference,
			new BigDecimal("1250.00"), "ARS", "123456", true, "CFP_OPAQUE", null,
			paymentStatus == null ? null : "PAYMENT_FIXTURE", paymentStatus,
			paymentStatus == null ? null : new BigDecimal("1250.00"), NOW);
	}

	private PaymentCredential credential() {
		return new PaymentCredential(
			"oauth-token-fixture", "123456", PaymentEnvironment.PRODUCTION,
			PaymentCredential.Source.TENANT_OAUTH);
	}

	private PaymentOAuthProperties oauthProperties() {
		return new PaymentOAuthProperties(
			true, PaymentEnvironment.PRODUCTION, "client", "secret",
			URI.create("https://example.test/callback"),
			URI.create("https://auth.test"), URI.create("https://api.test"),
			URI.create("https://identity.test"), URI.create("https://frontend.test"),
			Duration.ofSeconds(3), Duration.ofSeconds(8), "v1", "fixture-key");
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
