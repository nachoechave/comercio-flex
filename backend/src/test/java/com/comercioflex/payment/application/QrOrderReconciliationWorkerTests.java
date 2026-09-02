package com.comercioflex.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.tenant.application.TenantContext;

class QrOrderReconciliationWorkerTests {

	private static final Instant NOW = Instant.parse("2026-09-01T18:00:00Z");
	private final QrOrderControlRepository routes = mock(QrOrderControlRepository.class);
	private final PaymentCredentialResolver credentials = mock(PaymentCredentialResolver.class);
	private final QrOrderService orders = mock(QrOrderService.class);
	private final TenantContext tenantContext = new TenantContext();
	private QrOrderReconciliationWorker worker;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		TransactionTemplate transactions = mock(TransactionTemplate.class);
		when(transactions.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<Object> callback = invocation.getArgument(0);
			return callback.doInTransaction(mock(TransactionStatus.class));
		});
		org.mockito.Mockito.doAnswer(invocation -> {
			Consumer<TransactionStatus> callback = invocation.getArgument(0);
			callback.accept(mock(TransactionStatus.class));
			return null;
		}).when(transactions).executeWithoutResult(any());
		worker = new QrOrderReconciliationWorker(
			routes, credentials, orders, tenantContext, transactions,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void pendingOrderIsFetchedInsideTenantContextAndReleasedForRetry() {
		QrOrderRoute route = route();
		PaymentCredential credential = credential();
		when(routes.claimNext(any(), any())).thenReturn(Optional.of(route), Optional.empty());
		when(credentials.resolve(route.tenantId(), route.tenantSlug())).thenReturn(credential);
		when(orders.fetchAndApply(route, credential)).thenAnswer(invocation -> {
			assertThat(tenantContext.currentDatabaseKey()).contains("tenant_demo");
			return QrOrderProcessingResult.PENDING;
		});

		worker.reconcile();

		verify(routes).release(route.internalId(), route.attemptCount(), null,
			NOW.plusSeconds(10));
		assertThat(tenantContext.currentDatabaseKey()).isEmpty();
	}

	@Test
	void approvedOrderCompletesTheControlRoute() {
		QrOrderRoute route = route();
		PaymentCredential credential = credential();
		when(routes.claimNext(any(), any())).thenReturn(Optional.of(route), Optional.empty());
		when(credentials.resolve(route.tenantId(), route.tenantSlug())).thenReturn(credential);
		when(orders.fetchAndApply(route, credential))
			.thenReturn(QrOrderProcessingResult.APPROVED);

		worker.reconcile();

		verify(routes).complete(route.internalId(), "COMPLETED", NOW);
	}

	private QrOrderRoute route() {
		return new QrOrderRoute(
			7L, 1L, "tiendademo", "tenant_demo", PaymentEnvironment.PRODUCTION,
			UUID.randomUUID(), "provider-order", "seller", "ACTIVE", 2,
			NOW.plusSeconds(300));
	}

	private PaymentCredential credential() {
		return new PaymentCredential(
			"access-token", "seller", PaymentEnvironment.PRODUCTION,
			PaymentCredential.Source.TENANT_OAUTH);
	}
}
