package com.comercioflex.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

class TenantContextTests {

	private final TenantContext tenantContext = new TenantContext();

	@Test
	void rejectsBlankKeys() {
		assertThatThrownBy(() -> tenantContext.open(" "))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void removesTheKeyWhenTheScopeCloses() {
		try (TenantContext.Scope ignored = tenantContext.open("tenant-a")) {
			assertThat(tenantContext.currentDatabaseKey()).contains("tenant-a");
		}

		assertThat(tenantContext.currentDatabaseKey()).isEmpty();
	}

	@Test
	void rejectsClosingTheScopeFromAnotherThread() throws Exception {
		TenantContext.Scope scope = tenantContext.open("tenant-a");
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			assertThatThrownBy(() -> executor.submit(scope::close).get())
				.isInstanceOf(ExecutionException.class)
				.hasCauseInstanceOf(IllegalStateException.class);
			assertThat(tenantContext.currentDatabaseKey()).contains("tenant-a");
		}
		finally {
			executor.shutdownNow();
			scope.close();
		}
	}
}
