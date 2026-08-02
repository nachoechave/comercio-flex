package com.comercioflex.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.order.application.OrderTransitionExecution;
import com.comercioflex.order.application.PaidOrderConfirmer;
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
			tenantResolver,
			checkoutProperties,
			oauthProperties,
			immediateTransactions(),
			immediateTransactions(),
			Clock.fixed(NOW, ZoneOffset.UTC));
		when(repository.findByPublicId(ATTEMPT_ID, true))
			.thenReturn(Optional.of(storedAttempt));
		when(orderConfirmer.confirmWithinCurrentTransaction(any(), any()))
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
			storedAttempt.orderId(), storedAttempt.transitionIdempotencyKey());
		verify(repository).applyVerifiedPayment(
			same(storedAttempt), same(payment),
			org.mockito.ArgumentMatchers.eq(true),
			org.mockito.ArgumentMatchers.eq(false),
			org.mockito.ArgumentMatchers.eq(NOW));
		assertThat(result.paymentStatus()).isEqualTo("APPROVED");
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
