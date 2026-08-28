package com.comercioflex.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.payment.domain.PaymentResultStatus;
import com.comercioflex.tenant.application.TenantContext;

class PaymentWebhookWorkerTests {

	private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

	private final CheckoutControlRepository repository = mock(CheckoutControlRepository.class);
	private final PaymentCredentialResolver credentials = mock(PaymentCredentialResolver.class);
	private final CheckoutProGateway gateway = mock(CheckoutProGateway.class);
	private final CheckoutProService checkoutService = mock(CheckoutProService.class);
	private final CheckoutProProperties properties = mock(CheckoutProProperties.class);
	private final PaymentOAuthProperties oauthProperties = mock(PaymentOAuthProperties.class);
	private final TenantContext tenantContext = new TenantContext();
	private final PaymentWebhookMetrics metrics = mock(PaymentWebhookMetrics.class);
	private final PaymentWebhookWorker worker = new PaymentWebhookWorker(
		repository, credentials, gateway, checkoutService, properties,
		oauthProperties, tenantContext, immediateTransactions(), metrics,
		Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void resolvesAndProcessesEveryWebhookWithItsOwnTenantCredentialAndDatabase() {
		ClaimedWebhookEvent eventA = event(101L, "tienda-a", "tenant-a", "seller-a", "payment-a");
		ClaimedWebhookEvent eventB = event(202L, "tienda-b", "tenant-b", "seller-b", "payment-b");
		PaymentCredential credentialA = credential("token-a", "seller-a");
		PaymentCredential credentialB = credential("token-b", "seller-b");
		VerifiedProviderPayment paymentA = payment("payment-a", "seller-a");
		VerifiedProviderPayment paymentB = payment("payment-b", "seller-b");
		when(properties.webhookLease()).thenReturn(Duration.ofSeconds(30));
		when(oauthProperties.environment()).thenReturn(PaymentEnvironment.PRODUCTION);
		when(repository.claimNext(any(), any())).thenReturn(
			Optional.of(eventA), Optional.of(eventB), Optional.empty());
		when(credentials.resolve(101L, "tienda-a")).thenReturn(credentialA);
		when(credentials.resolve(202L, "tienda-b")).thenReturn(credentialB);
		when(gateway.findPayment(credentialA, "payment-a")).thenReturn(paymentA);
		when(gateway.findPayment(credentialB, "payment-b")).thenReturn(paymentB);
		when(repository.markProcessed(anyLong(), anyInt(), any()))
			.thenReturn(true);
		doAnswer(invocation -> {
			UUID attemptId = invocation.getArgument(0);
			String expected = attemptId.equals(eventA.route().paymentAttemptId())
				? "tenant-a" : "tenant-b";
			assertThat(tenantContext.currentDatabaseKey()).contains(expected);
			return null;
		}).when(checkoutService).applyVerifiedPayment(any(), any());

		worker.drain();

		verify(gateway).findPayment(credentialA, "payment-a");
		verify(gateway).findPayment(credentialB, "payment-b");
		verify(checkoutService).applyVerifiedPayment(eventA.route().paymentAttemptId(), paymentA);
		verify(checkoutService).applyVerifiedPayment(eventB.route().paymentAttemptId(), paymentB);
		assertThat(tenantContext.currentDatabaseKey()).isEmpty();
	}

	@Test
	void sellerMismatchFailsClosedBeforeProviderOrTenantDatabaseAccess() {
		ClaimedWebhookEvent event = event(
			101L, "tienda-a", "tenant-a", "expected-seller", "payment-a");
		when(properties.webhookLease()).thenReturn(Duration.ofSeconds(30));
		when(properties.retryDelay()).thenReturn(Duration.ofSeconds(30));
		when(properties.maxWebhookAttempts()).thenReturn(8);
		when(oauthProperties.environment()).thenReturn(PaymentEnvironment.PRODUCTION);
		when(repository.claimNext(any(), any())).thenReturn(Optional.of(event), Optional.empty());
		when(credentials.resolve(101L, "tienda-a"))
			.thenReturn(credential("wrong-token", "another-seller"));
		when(repository.markFailed(anyLong(), anyInt(), anyBoolean(),
			any(String.class), any())).thenReturn(true);

		worker.drain();

		verify(gateway, never()).findPayment(any(), any());
		verify(checkoutService, never()).applyVerifiedPayment(any(), any());
		verify(repository).markFailed(
			eq(event.internalId()), eq(event.attemptCount()), eq(true),
			eq("WEBHOOK_CREDENTIAL_MISMATCH"), eq(NOW.plusSeconds(30)));
		assertThat(tenantContext.currentDatabaseKey()).isEmpty();
	}

	private ClaimedWebhookEvent event(
			long tenantId, String slug, String databaseKey,
			String expectedSeller, String paymentId) {
		CheckoutRoute route = new CheckoutRoute(
			tenantId, UUID.randomUUID(), tenantId, slug, databaseKey,
			PaymentEnvironment.PRODUCTION, UUID.randomUUID(), expectedSeller,
			"preference-" + slug, "ACTIVE", NOW.plusSeconds(1800));
		return new ClaimedWebhookEvent(
			tenantId, UUID.randomUUID(), 1, paymentId, route);
	}

	private PaymentCredential credential(String token, String seller) {
		return new PaymentCredential(
			token, seller, PaymentEnvironment.PRODUCTION,
			PaymentCredential.Source.TENANT_OAUTH);
	}

	private VerifiedProviderPayment payment(String id, String seller) {
		return new VerifiedProviderPayment(
			id, seller, "preference", "external", BigDecimal.TEN, "ARS", true,
			PaymentResultStatus.APPROVED, NOW);
	}

	@SuppressWarnings("unchecked")
	private TransactionTemplate immediateTransactions() {
		TransactionTemplate transactions = mock(TransactionTemplate.class);
		when(transactions.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<Object> callback = invocation.getArgument(0);
			return callback.doInTransaction(mock(TransactionStatus.class));
		});
		return transactions;
	}
}
