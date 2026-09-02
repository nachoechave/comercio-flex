package com.comercioflex.payment.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.tenant.application.TenantContext;

@Component
@ConditionalOnProperty(
	prefix = "app.payments.checkout-pro", name = "enabled", havingValue = "true")
public class QrOrderReconciliationWorker {

	private static final Logger LOGGER =
		LoggerFactory.getLogger(QrOrderReconciliationWorker.class);
	private static final int BATCH_SIZE = 20;
	private static final Duration LEASE = Duration.ofSeconds(30);
	private static final Duration RETRY_DELAY = Duration.ofSeconds(10);

	private final QrOrderControlRepository routes;
	private final PaymentCredentialResolver credentials;
	private final QrOrderService orders;
	private final TenantContext tenantContext;
	private final TransactionTemplate controlTransactions;
	private final Clock clock;
	private final AtomicBoolean running = new AtomicBoolean();

	@Autowired
	public QrOrderReconciliationWorker(
			QrOrderControlRepository routes,
			PaymentCredentialResolver credentials,
			QrOrderService orders,
			TenantContext tenantContext,
			@Qualifier("controlTransactionTemplate") TransactionTemplate controlTransactions) {
		this(routes, credentials, orders, tenantContext, controlTransactions,
			Clock.systemUTC());
	}

	QrOrderReconciliationWorker(
			QrOrderControlRepository routes,
			PaymentCredentialResolver credentials,
			QrOrderService orders,
			TenantContext tenantContext,
			TransactionTemplate controlTransactions,
			Clock clock) {
		this.routes = routes;
		this.credentials = credentials;
		this.orders = orders;
		this.tenantContext = tenantContext;
		this.controlTransactions = controlTransactions;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${app.payments.qr-orders.reconciliation-delay-ms:10000}")
	public void reconcile() {
		if (!running.compareAndSet(false, true)) return;
		try {
			for (int index = 0; index < BATCH_SIZE; index++) {
				Optional<QrOrderRoute> claimed = controlTransactions.execute(status ->
					routes.claimNext(clock.instant(), clock.instant().plus(LEASE)));
				if (claimed == null || claimed.isEmpty()) return;
				process(claimed.get());
			}
		}
		finally {
			running.set(false);
		}
	}

	private void process(QrOrderRoute route) {
		try {
			PaymentCredential credential = credentials.resolve(route.tenantId(), route.tenantSlug());
			QrOrderProcessingResult result;
			try (TenantContext.Scope ignored = tenantContext.open(route.tenantDatabaseKey())) {
				result = orders.fetchAndApply(route, credential);
			}
			if (result == QrOrderProcessingResult.PENDING) {
				release(route, null);
			}
			else {
				controlTransactions.executeWithoutResult(status -> routes.complete(
					route.internalId(), result == QrOrderProcessingResult.EXPIRED
						? "EXPIRED" : "COMPLETED", clock.instant()));
			}
		}
		catch (RuntimeException exception) {
			String code = exception instanceof QrOrderException qr
				? safe(qr.code()) : "QR_RECONCILIATION_FAILED";
			release(route, code);
			LOGGER.warn(
				"event=mp_qr_order_reconciliation_failed tenant={} environment={} errorType={}",
				route.tenantSlug(), route.environment(), exception.getClass().getSimpleName());
		}
	}

	private void release(QrOrderRoute route, String code) {
		controlTransactions.executeWithoutResult(status -> routes.release(
			route.internalId(), route.attemptCount(), code,
			clock.instant().plus(RETRY_DELAY)));
	}

	private String safe(String value) {
		return value != null && value.matches("^[A-Z0-9_]{1,64}$")
			? value : "QR_RECONCILIATION_FAILED";
	}
}
