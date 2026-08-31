package com.comercioflex.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.comercioflex.tenant.application.TenantConnectionCatalog;
import com.comercioflex.tenant.application.TenantContext;
import com.comercioflex.tenant.infrastructure.control.ActiveTenant;
import com.comercioflex.tenant.infrastructure.control.TenantRepository;

class CheckoutReconciliationWorkerTests {
	private static final ActiveTenant TENANT =
		new ActiveTenant(1L, "tiendademo", "Tienda Demo", "tenant_demo");

	@Test
	void disabledCheckoutProDoesNotInspectTenants() {
		TenantRepository tenants = mock(TenantRepository.class);
		CheckoutProService checkoutPro = mock(CheckoutProService.class);

		worker(tenants, mock(TenantConnectionCatalog.class), new TenantContext(), checkoutPro,
			properties(false)).reconcile();

		verify(tenants, never()).findAllActive();
		verify(checkoutPro, never()).reconcilePendingTenant(1L, "tiendademo");
	}

	@Test
	void activeReadyTenantIsReconciledServerSideInsideItsTenantContext() {
		TenantRepository tenants = mock(TenantRepository.class);
		TenantConnectionCatalog connections = mock(TenantConnectionCatalog.class);
		CheckoutProService checkoutPro = mock(CheckoutProService.class);
		TenantContext context = new TenantContext();
		when(tenants.findAllActive()).thenReturn(List.of(TENANT));
		when(connections.contains("tenant_demo")).thenReturn(true);
		doAnswer(invocation -> {
			assertThat(context.currentDatabaseKey()).contains("tenant_demo");
			return 1;
		}).when(checkoutPro).reconcilePendingTenant(1L, "tiendademo");

		worker(tenants, connections, context, checkoutPro, properties(true)).reconcile();

		verify(checkoutPro).reconcilePendingTenant(1L, "tiendademo");
		assertThat(context.currentDatabaseKey()).isEmpty();
	}

	@Test
	void tenantWithoutAReadyDatabaseConnectionIsSkipped() {
		TenantRepository tenants = mock(TenantRepository.class);
		TenantConnectionCatalog connections = mock(TenantConnectionCatalog.class);
		CheckoutProService checkoutPro = mock(CheckoutProService.class);
		when(tenants.findAllActive()).thenReturn(List.of(TENANT));
		when(connections.contains("tenant_demo")).thenReturn(false);

		worker(tenants, connections, new TenantContext(), checkoutPro,
			properties(true)).reconcile();

		verify(checkoutPro, never()).reconcilePendingTenant(1L, "tiendademo");
	}

	private CheckoutReconciliationWorker worker(TenantRepository tenants,
			TenantConnectionCatalog connections, TenantContext context,
			CheckoutProService checkoutPro, CheckoutProProperties properties) {
		return new CheckoutReconciliationWorker(
			tenants, connections, context, checkoutPro, properties);
	}

	private CheckoutProProperties properties(boolean enabled) {
		return new CheckoutProProperties(enabled, "test-token", "123456", "tiendademo",
			URI.create("https://api.example.test"), URI.create("https://shop.example.test"),
			"webhook-secret", Duration.ofSeconds(1), Duration.ofSeconds(2),
			Duration.ofSeconds(30), Duration.ofSeconds(30), 3, Duration.ofHours(24));
	}
}
