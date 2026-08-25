package com.comercioflex.notification.infrastructure;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.comercioflex.tenant.application.TenantConnectionCatalog;
import com.comercioflex.tenant.application.TenantContext;
import com.comercioflex.tenant.infrastructure.control.ActiveTenant;
import com.comercioflex.tenant.infrastructure.control.TenantRepository;

/**
 * Polls every ready tenant independently. A unique event key makes enqueueing
 * idempotent, while SMTP delivery is at-least-once: a process crash after SMTP
 * accepts a message but before SENT is persisted can cause a later duplicate.
 */
@Component
class TransactionalEmailOutboxWorker {
	private static final Logger LOGGER = LoggerFactory.getLogger(TransactionalEmailOutboxWorker.class);

	private final TenantRepository tenants;
	private final TenantConnectionCatalog connections;
	private final TenantContext tenantContext;
	private final TransactionalEmailOutboxBatchProcessor processor;
	private final EmailProperties properties;
	private final AtomicBoolean running = new AtomicBoolean();

	TransactionalEmailOutboxWorker(TenantRepository tenants, TenantConnectionCatalog connections,
			TenantContext tenantContext, TransactionalEmailOutboxBatchProcessor processor,
			EmailProperties properties) {
		this.tenants = tenants;
		this.connections = connections;
		this.tenantContext = tenantContext;
		this.processor = processor;
		this.properties = properties;
	}

	@Scheduled(fixedDelayString = "${app.email.outbox-poll-interval-ms:30000}")
	void poll() {
		if (!properties.isEnabled() || !properties.isOutboxWorkerEnabled()) return;
		if (!running.compareAndSet(false, true)) return;
		try {
			for (ActiveTenant tenant : tenants.findAllActive()) {
				processTenant(tenant);
			}
		}
		finally {
			running.set(false);
		}
	}

	private void processTenant(ActiveTenant tenant) {
		if (!connections.contains(tenant.databaseKey())) {
			LOGGER.warn("email_outbox_tenant_skipped tenant={} reason=connection_not_ready",
				tenant.slug());
			return;
		}
		LOGGER.info("email_outbox_processing_started tenant={} batch_size={}",
			tenant.slug(), properties.getOutboxBatchSize());
		try (TenantContext.Scope ignored = tenantContext.open(tenant.databaseKey())) {
			int processed = processor.processBatch();
			LOGGER.info("email_outbox_processing_completed tenant={} processed={}",
				tenant.slug(), processed);
		}
		catch (RuntimeException exception) {
			LOGGER.error("email_outbox_processing_failed tenant={} error_type={}",
				tenant.slug(), exception.getClass().getSimpleName());
		}
	}
}
