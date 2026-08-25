package com.comercioflex.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.comercioflex.tenant.application.TenantConnectionCatalog;
import com.comercioflex.tenant.application.TenantContext;
import com.comercioflex.tenant.infrastructure.control.ActiveTenant;
import com.comercioflex.tenant.infrastructure.control.TenantRepository;

class TransactionalEmailOutboxWorkerTests {
	private static final ActiveTenant TENANT_A = new ActiveTenant(1L, "tienda-a", "Tienda A", "tenant_a");
	private static final ActiveTenant TENANT_B = new ActiveTenant(2L, "tienda-b", "Tienda B", "tenant_b");

	@Test
	void disabledEmailDoesNotInspectTenantsOrProcessOutbox() {
		TenantRepository tenants = mock(TenantRepository.class);
		TransactionalEmailOutboxBatchProcessor processor = mock(TransactionalEmailOutboxBatchProcessor.class);
		EmailProperties properties = new EmailProperties();

		worker(tenants, mock(TenantConnectionCatalog.class), new TenantContext(), processor,
			properties).poll();

		verify(tenants, never()).findAllActive();
		verify(processor, never()).processBatch();
	}

	@Test
	void disabledWorkerDoesNotInspectTenantsEvenWhenEmailIsEnabled() {
		TenantRepository tenants = mock(TenantRepository.class);
		TransactionalEmailOutboxBatchProcessor processor = mock(TransactionalEmailOutboxBatchProcessor.class);
		EmailProperties properties = enabledProperties();
		properties.setOutboxWorkerEnabled(false);

		worker(tenants, mock(TenantConnectionCatalog.class), new TenantContext(), processor,
			properties).poll();

		verify(tenants, never()).findAllActive();
		verify(processor, never()).processBatch();
	}

	@Test
	void activeReadyTenantsAreProcessedWithIndependentCleanContexts() {
		TenantRepository tenants = mock(TenantRepository.class);
		TenantConnectionCatalog connections = mock(TenantConnectionCatalog.class);
		TransactionalEmailOutboxBatchProcessor processor = mock(TransactionalEmailOutboxBatchProcessor.class);
		TenantContext context = new TenantContext();
		when(tenants.findAllActive()).thenReturn(List.of(TENANT_A, TENANT_B));
		when(connections.contains("tenant_a")).thenReturn(true);
		when(connections.contains("tenant_b")).thenReturn(true);
		List<String> observed = new ArrayList<>();
		doAnswer(invocation -> {
			observed.add(context.currentDatabaseKey().orElseThrow());
			return 1;
		}).when(processor).processBatch();

		worker(tenants, connections, context, processor, enabledProperties()).poll();

		assertThat(observed).containsExactly("tenant_a", "tenant_b");
		assertThat(context.currentDatabaseKey()).isEmpty();
	}

	@Test
	void tenantFailureDoesNotStopTheNextTenantAndContextIsCleaned() {
		TenantRepository tenants = mock(TenantRepository.class);
		TenantConnectionCatalog connections = mock(TenantConnectionCatalog.class);
		TransactionalEmailOutboxBatchProcessor processor = mock(TransactionalEmailOutboxBatchProcessor.class);
		TenantContext context = new TenantContext();
		when(tenants.findAllActive()).thenReturn(List.of(TENANT_A, TENANT_B));
		when(connections.contains("tenant_a")).thenReturn(true);
		when(connections.contains("tenant_b")).thenReturn(true);
		doThrow(new IllegalStateException("tenant A unavailable"))
			.doAnswer(invocation -> {
				assertThat(context.currentDatabaseKey()).contains("tenant_b");
				return 1;
			}).when(processor).processBatch();

		worker(tenants, connections, context, processor, enabledProperties()).poll();

		verify(processor, org.mockito.Mockito.times(2)).processBatch();
		assertThat(context.currentDatabaseKey()).isEmpty();
	}

	@Test
	void overlappingPollInTheSameInstanceIsIgnored() throws Exception {
		TenantRepository tenants = mock(TenantRepository.class);
		TenantConnectionCatalog connections = mock(TenantConnectionCatalog.class);
		TransactionalEmailOutboxBatchProcessor processor = mock(TransactionalEmailOutboxBatchProcessor.class);
		when(tenants.findAllActive()).thenReturn(List.of(TENANT_A));
		when(connections.contains("tenant_a")).thenReturn(true);
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		doAnswer(invocation -> {
			entered.countDown();
			release.await(5, TimeUnit.SECONDS);
			return 1;
		}).when(processor).processBatch();
		TransactionalEmailOutboxWorker worker = worker(tenants, connections,
			new TenantContext(), processor, enabledProperties());
		Thread first = new Thread(worker::poll);

		first.start();
		assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
		worker.poll();
		release.countDown();
		first.join(5_000);

		verify(processor).processBatch();
	}

	private TransactionalEmailOutboxWorker worker(TenantRepository tenants,
			TenantConnectionCatalog connections, TenantContext context,
			TransactionalEmailOutboxBatchProcessor processor, EmailProperties properties) {
		return new TransactionalEmailOutboxWorker(
			tenants, connections, context, processor, properties);
	}

	private EmailProperties enabledProperties() {
		EmailProperties properties = new EmailProperties();
		properties.setEnabled(true);
		properties.setOutboxWorkerEnabled(true);
		return properties;
	}
}
