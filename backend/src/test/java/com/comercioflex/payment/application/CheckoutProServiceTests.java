package com.comercioflex.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.order.application.OrderTransitionExecution;
import com.comercioflex.order.application.PaidOrderConfirmer;
import com.comercioflex.order.application.AdminOrderRepository;
import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.payment.domain.PaymentIntentStatus;
import com.comercioflex.payment.domain.PaymentResultStatus;
import com.comercioflex.tenant.application.ResolvedTenant;
import com.comercioflex.tenant.application.TenantResolver;

class CheckoutProServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-01T22:15:00Z");
	private static final UUID ATTEMPT_ID = UUID.fromString(
		"11111111-1111-4111-8111-111111111111");
	private final CheckoutRepository repository = mock(CheckoutRepository.class);
	private final PaidOrderConfirmer orderConfirmer = mock(PaidOrderConfirmer.class);
	private final RejectedPaymentNotifier rejectedPaymentNotifier = mock(RejectedPaymentNotifier.class);
	private final AdminOrderRepository adminOrders = mock(AdminOrderRepository.class);
	private final PaymentCredentialResolver credentials = mock(PaymentCredentialResolver.class);
	private final CheckoutProGateway gateway = mock(CheckoutProGateway.class);
	private final TenantResolver tenantResolver = mock(TenantResolver.class);
	private final CheckoutProProperties checkoutProperties = mock(CheckoutProProperties.class);
	private final PaymentOAuthProperties oauthProperties = mock(PaymentOAuthProperties.class);
	private final StoredCheckoutAttempt storedAttempt = attempt();
	private CheckoutProService service;

	@BeforeEach
	void setUp() {
		service = new CheckoutProService(
			repository,
			mock(CheckoutControlRepository.class),
			credentials,
			gateway,
			orderConfirmer,
			rejectedPaymentNotifier,
			adminOrders,
			tenantResolver,
			checkoutProperties,
			oauthProperties,
			immediateTransactions(),
			immediateTransactions(),
			Clock.fixed(NOW, ZoneOffset.UTC));
		when(repository.findByPublicId(ATTEMPT_ID, true))
			.thenReturn(Optional.of(storedAttempt));
		when(orderConfirmer.confirmWithinCurrentTransaction(any(), any(), anyString()))
			.thenReturn(OrderTransitionExecution.completed(null));
		when(oauthProperties.environment()).thenReturn(PaymentEnvironment.TEST);
		when(checkoutProperties.enabled()).thenReturn(true);
	}

	@Test
	void acceptsCheckoutProTestPaymentReportedAsLive() {
		VerifiedProviderPayment payment = payment("seller-1", true);

		service.applyVerifiedPayment(ATTEMPT_ID, payment);

		verify(repository).applyVerifiedPayment(
			same(storedAttempt), same(payment),
			org.mockito.ArgumentMatchers.eq(true),
			org.mockito.ArgumentMatchers.eq(false),
			org.mockito.ArgumentMatchers.eq(NOW));
	}

	@Test
	void stillRejectsPaymentFromAnotherSeller() {
		VerifiedProviderPayment payment = payment("seller-2", true);

		assertThatThrownBy(() -> service.applyVerifiedPayment(ATTEMPT_ID, payment))
			.isInstanceOf(CheckoutPaymentException.class)
			.extracting(exception -> ((CheckoutPaymentException) exception).code())
			.isEqualTo("PAYMENT_VALIDATION_FAILED");
	}

	@Test
	void rejectsMismatchedAmountCurrencyPreferenceAndExternalReference() {
		VerifiedProviderPayment[] mismatches = {
			new VerifiedProviderPayment(
				"payment-6", "seller-1", "pref-6", "external-6",
				new BigDecimal("16900.76"), "ARS", true,
				PaymentResultStatus.APPROVED, NOW),
			new VerifiedProviderPayment(
				"payment-6", "seller-1", "pref-6", "external-6",
				new BigDecimal("16900.75"), "USD", true,
				PaymentResultStatus.APPROVED, NOW),
			new VerifiedProviderPayment(
				"payment-6", "seller-1", "other-pref", "external-6",
				new BigDecimal("16900.75"), "ARS", true,
				PaymentResultStatus.APPROVED, NOW),
			new VerifiedProviderPayment(
				"payment-6", "seller-1", "pref-6", "other-reference",
				new BigDecimal("16900.75"), "ARS", true,
				PaymentResultStatus.APPROVED, NOW)
		};

		for (VerifiedProviderPayment mismatch : mismatches) {
			assertThatThrownBy(() -> service.applyVerifiedPayment(ATTEMPT_ID, mismatch))
				.isInstanceOf(CheckoutPaymentException.class)
				.extracting(exception -> ((CheckoutPaymentException) exception).code())
				.isEqualTo("PAYMENT_VALIDATION_FAILED");
		}
	}

	@Test
	void reconcilesReturnByFetchingAndValidatingTheProviderPayment() {
		String token = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";
		ResolvedTenant tenant = new ResolvedTenant(1L, "tienda-a", "Tienda A", "tenant_a");
		PaymentCredential credential = new PaymentCredential(
			"access-token", "seller-1", PaymentEnvironment.TEST,
			PaymentCredential.Source.CENTRAL_TEST);
		VerifiedProviderPayment payment = payment("seller-1", true);
		when(tenantResolver.resolveActive("tienda-a")).thenReturn(tenant);
		when(repository.findByReturnTokenHash(any())).thenReturn(Optional.of(storedAttempt));
		when(credentials.resolve(tenant.id(), tenant.slug())).thenReturn(credential);
		when(gateway.findPayment(credential, "171652320068")).thenReturn(payment);
		when(repository.latestProviderStatus(storedAttempt.internalId())).thenReturn("APPROVED");

		PaymentReturnView result = service.reconcileReturn(
			"tienda-a", token, "171652320068");

		verify(gateway).findPayment(credential, "171652320068");
		verify(orderConfirmer).confirmWithinCurrentTransaction(
			storedAttempt.orderId(), storedAttempt.transitionIdempotencyKey(), "Mercado Pago");
		verify(repository).applyVerifiedPayment(
			same(storedAttempt), same(payment),
			org.mockito.ArgumentMatchers.eq(true),
			org.mockito.ArgumentMatchers.eq(false),
			org.mockito.ArgumentMatchers.eq(NOW));
		assertThat(result.paymentStatus()).isEqualTo("APPROVED");
	}

	@Test
	void reconcilesReturnByPreferenceWithoutTrustingBrowserQueryParameters() {
		String token = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";
		ResolvedTenant tenant = new ResolvedTenant(1L, "tienda-a", "Tienda A", "tenant_a");
		PaymentCredential credential = new PaymentCredential(
			"access-token", "seller-1", PaymentEnvironment.TEST,
			PaymentCredential.Source.CENTRAL_TEST);
		VerifiedProviderPayment payment = payment("seller-1", true);
		when(tenantResolver.resolveActive("tienda-a")).thenReturn(tenant);
		when(repository.findByReturnTokenHash(any())).thenReturn(Optional.of(storedAttempt));
		when(credentials.resolve(tenant.id(), tenant.slug())).thenReturn(credential);
		when(gateway.findPaymentForPreference(
			credential, storedAttempt.preferenceId(), storedAttempt.externalReference()))
			.thenReturn(Optional.of(payment));
		when(repository.latestProviderStatus(storedAttempt.internalId())).thenReturn("APPROVED");

		PaymentReturnView result = service.reconcileReturn("tienda-a", token);

		assertThat(result.paymentStatus()).isEqualTo("APPROVED");
		verify(gateway).findPaymentForPreference(
			credential, storedAttempt.preferenceId(), storedAttempt.externalReference());
		verify(orderConfirmer).confirmWithinCurrentTransaction(
			storedAttempt.orderId(), storedAttempt.transitionIdempotencyKey(), "Mercado Pago");
	}

	@Test
	void verifiedRejectedPaymentBecomesRetryableAndCreatesOneOutboxEvent() {
		VerifiedProviderPayment rejected = new VerifiedProviderPayment(
			"payment-6", "seller-1", "pref-6", "external-6",
			new BigDecimal("16900.75"), "ARS", true,
			PaymentResultStatus.REJECTED, NOW);

		service.applyVerifiedPayment(ATTEMPT_ID, rejected);

		verify(repository).applyVerifiedPayment(
			same(storedAttempt), same(rejected),
			org.mockito.ArgumentMatchers.eq(false),
			org.mockito.ArgumentMatchers.eq(false),
			org.mockito.ArgumentMatchers.eq(NOW));
		verify(rejectedPaymentNotifier).notifyWithinCurrentTransaction(
			storedAttempt.orderId(), storedAttempt.id(), NOW);
		verify(orderConfirmer, never()).confirmWithinCurrentTransaction(any(), any(), anyString());
	}

	@Test
	void backendReconciliationConfirmsWithoutAFrontendRequest() {
		PaymentCredential credential = new PaymentCredential(
			"access-token", "seller-1", PaymentEnvironment.TEST,
			PaymentCredential.Source.CENTRAL_TEST);
		VerifiedProviderPayment payment = payment("seller-1", true);
		when(credentials.resolve(1L, "tienda-a")).thenReturn(credential);
		when(repository.findPendingForReconciliation(20)).thenReturn(List.of(storedAttempt));
		when(gateway.findPaymentForPreference(
			credential, storedAttempt.preferenceId(), storedAttempt.externalReference()))
			.thenReturn(Optional.of(payment));

		int processed = service.reconcilePendingTenant(1L, "tienda-a");

		assertThat(processed).isEqualTo(1);
		verify(orderConfirmer).confirmWithinCurrentTransaction(
			storedAttempt.orderId(), storedAttempt.transitionIdempotencyKey(), "Mercado Pago");
	}

	@Test
	void backendReconciliationExpiresAnUnpaidCheckoutAfterTheGraceWindow() {
		StoredCheckoutAttempt expired = expiredAttempt();
		PaymentCredential credential = new PaymentCredential(
			"access-token", "seller-1", PaymentEnvironment.TEST,
			PaymentCredential.Source.CENTRAL_TEST);
		when(credentials.resolve(1L, "tienda-a")).thenReturn(credential);
		when(repository.findPendingForReconciliation(20)).thenReturn(List.of(expired));
		when(repository.findByPublicId(expired.id(), true)).thenReturn(Optional.of(expired));
		when(gateway.findPaymentForPreference(
			credential, expired.preferenceId(), expired.externalReference()))
			.thenReturn(Optional.empty());

		int processed = service.reconcilePendingTenant(1L, "tienda-a");

		assertThat(processed).isEqualTo(1);
		verify(repository).markExpired(expired, NOW);
		verify(adminOrders).expireOrder(expired.orderInternalId());
	}

	@Test
	void exposesNoPaymentAsInformationWithoutChangingBusinessState() {
		String token = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";
		ResolvedTenant tenant = new ResolvedTenant(1L, "tienda-a", "Tienda A", "tenant_a");
		PaymentCredential credential = new PaymentCredential(
			"access-token", "seller-1", PaymentEnvironment.TEST,
			PaymentCredential.Source.CENTRAL_TEST);
		when(tenantResolver.resolveActive("tienda-a")).thenReturn(tenant);
		when(repository.findByReturnTokenHash(any())).thenReturn(Optional.of(storedAttempt));
		when(credentials.resolve(tenant.id(), tenant.slug())).thenReturn(credential);
		when(gateway.inspectPreference(credential, "pref-6", "external-6"))
			.thenReturn(ProviderCheckoutState.NO_PAYMENT_RECORDED);

		PaymentReturnView result = service.inspectReturn("tienda-a", token);

		assertThat(result.paymentStatus()).isEqualTo("PENDING");
		assertThat(result.returnOutcome()).isEqualTo(PaymentReturnOutcome.PAYMENT_NOT_RECORDED);
		assertThat(result.canRetry()).isTrue();
		verify(gateway).inspectPreference(credential, "pref-6", "external-6");
		org.mockito.Mockito.verifyNoInteractions(orderConfirmer);
		org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
			.applyVerifiedPayment(
				any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
				org.mockito.ArgumentMatchers.anyBoolean(), any());
	}

	@Test
	void reconcilesPendingPrivateOrderByDiscoveringItsProviderPayment() {
		String lookupToken = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";
		ResolvedTenant tenant = new ResolvedTenant(1L, "tienda-a", "Tienda A", "tenant_a");
		PaymentCredential credential = new PaymentCredential(
			"access-token", "seller-1", PaymentEnvironment.TEST,
			PaymentCredential.Source.CENTRAL_TEST);
		VerifiedProviderPayment payment = payment("seller-1", true);
		when(tenantResolver.resolveActive("tienda-a")).thenReturn(tenant);
		when(repository.findPendingByOrder(
			org.mockito.ArgumentMatchers.eq(storedAttempt.orderId()), any()))
			.thenReturn(Optional.of(storedAttempt));
		when(credentials.resolve(tenant.id(), tenant.slug())).thenReturn(credential);
		when(gateway.findPaymentForPreference(
			credential, storedAttempt.preferenceId(), storedAttempt.externalReference()))
			.thenReturn(Optional.of(payment));

		boolean reconciled = service.reconcilePendingOrder(
			"tienda-a", storedAttempt.orderId(), lookupToken);

		assertThat(reconciled).isTrue();
		verify(gateway).findPaymentForPreference(
			credential, storedAttempt.preferenceId(), storedAttempt.externalReference());
		verify(orderConfirmer).confirmWithinCurrentTransaction(
			storedAttempt.orderId(), storedAttempt.transitionIdempotencyKey(), "Mercado Pago");
	}

	@Test
	void leavesPendingPrivateOrderUntouchedWhenProviderHasNoPayment() {
		String lookupToken = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";
		ResolvedTenant tenant = new ResolvedTenant(1L, "tienda-a", "Tienda A", "tenant_a");
		PaymentCredential credential = new PaymentCredential(
			"access-token", "seller-1", PaymentEnvironment.TEST,
			PaymentCredential.Source.CENTRAL_TEST);
		when(tenantResolver.resolveActive("tienda-a")).thenReturn(tenant);
		when(repository.findPendingByOrder(
			org.mockito.ArgumentMatchers.eq(storedAttempt.orderId()), any()))
			.thenReturn(Optional.of(storedAttempt));
		when(credentials.resolve(tenant.id(), tenant.slug())).thenReturn(credential);
		when(gateway.findPaymentForPreference(
			credential, storedAttempt.preferenceId(), storedAttempt.externalReference()))
			.thenReturn(Optional.empty());

		boolean reconciled = service.reconcilePendingOrder(
			"tienda-a", storedAttempt.orderId(), lookupToken);

		assertThat(reconciled).isFalse();
		org.mockito.Mockito.verifyNoInteractions(orderConfirmer);
		verify(repository, org.mockito.Mockito.never()).applyVerifiedPayment(
			any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
			org.mockito.ArgumentMatchers.anyBoolean(), any());
	}

	private StoredCheckoutAttempt attempt() {
		return new StoredCheckoutAttempt(
			2L, ATTEMPT_ID, 6L,
			UUID.fromString("22222222-2222-4222-8222-222222222222"),
			6L, "PENDING_CONFIRMATION", NOW.plusSeconds(1800),
			UUID.fromString("33333333-3333-4333-8333-333333333333"),
			new byte[32],
			UUID.fromString("44444444-4444-4444-8444-444444444444"),
			PaymentIntentStatus.PENDING, new BigDecimal("16900.75"), "ARS",
			"external-6", NOW.plusSeconds(3600), "pref-6",
			URI.create("https://sandbox.mercadopago.com.ar/checkout"),
			NOW.plusSeconds(1800), "seller-1", PaymentEnvironment.TEST, NOW, 1L);
	}

	private VerifiedProviderPayment payment(String sellerAccountId, boolean liveMode) {
		return new VerifiedProviderPayment(
			"payment-6", sellerAccountId, "pref-6", "external-6",
			new BigDecimal("16900.75"), "ARS", liveMode,
			PaymentResultStatus.APPROVED, NOW);
	}

	private StoredCheckoutAttempt expiredAttempt() {
		StoredCheckoutAttempt current = attempt();
		return new StoredCheckoutAttempt(
			current.internalId(), current.id(), current.orderInternalId(), current.orderId(),
			current.orderNumber(), current.orderStatus(), NOW.minusSeconds(600),
			current.idempotencyKey(), current.requestFingerprint(),
			current.transitionIdempotencyKey(), current.status(), current.amount(),
			current.currencyCode(), current.externalReference(), current.returnTokenExpiresAt(),
			current.preferenceId(), current.checkoutUri(), NOW.minusSeconds(301),
			current.sellerAccountId(), current.environment(), current.updatedAt(), current.version());
	}

	private TransactionTemplate immediateTransactions() {
		TransactionTemplate transactions = mock(TransactionTemplate.class);
		org.mockito.Mockito.doAnswer(invocation -> {
			TransactionCallback<?> callback = invocation.getArgument(0);
			return callback.doInTransaction(mock(TransactionStatus.class));
		}).when(transactions).execute(any(TransactionCallback.class));
		org.mockito.Mockito.doAnswer(invocation -> {
			Consumer<TransactionStatus> callback = invocation.getArgument(0);
			callback.accept(mock(TransactionStatus.class));
			return null;
		}).when(transactions).executeWithoutResult(any());
		return transactions;
	}
}
