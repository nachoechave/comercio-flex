package com.comercioflex.payment.application;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.comercioflex.tenant.application.TenantConnectionCatalog;
import com.comercioflex.tenant.application.TenantContext;
import com.comercioflex.tenant.infrastructure.control.ActiveTenant;
import com.comercioflex.tenant.infrastructure.control.TenantRepository;

@Component
public class CheckoutReconciliationWorker {

	private static final Logger LOGGER = LoggerFactory.getLogger(
		CheckoutReconciliationWorker.class);

	private final TenantRepository tenants;
	private final TenantConnectionCatalog connections;
	private final TenantContext tenantContext;
	private final CheckoutProService checkoutPro;
	private final CheckoutProProperties properties;
	private final AtomicBoolean running = new AtomicBoolean();

	public CheckoutReconciliationWorker(
			TenantRepository tenants,
			TenantConnectionCatalog connections,
			TenantContext tenantContext,
			CheckoutProService checkoutPro,
			CheckoutProProperties properties) {
		this.tenants = tenants;
		this.connections = connections;
		this.tenantContext = tenantContext;
		this.checkoutPro = checkoutPro;
		this.properties = properties;
	}

	@Scheduled(fixedDelayString = "${app.payments.checkout-pro.reconciliation-delay-ms:10000}")
	public void reconcile() {
		if (!properties.enabled() || !running.compareAndSet(false, true)) return;
		try {
			for (ActiveTenant tenant : tenants.findAllActive()) {
				reconcileTenant(tenant);
			}
		}
		finally {
			running.set(false);
		}
	}

	private void reconcileTenant(ActiveTenant tenant) {
		if (!connections.contains(tenant.databaseKey())) return;
		try (TenantContext.Scope ignored = tenantContext.open(tenant.databaseKey())) {
			int processed = checkoutPro.reconcilePendingTenant(tenant.id(), tenant.slug());
			if (processed > 0) {
				LOGGER.info("payment_reconciliation_completed tenant={} processed={}",
					tenant.slug(), processed);
			}
		}
		catch (RuntimeException exception) {
			LOGGER.warn("payment_reconciliation_tenant_failed tenant={} error_type={}",
				tenant.slug(), exception.getClass().getSimpleName());
		}
	}
}
