package com.comercioflex.tenant.application;

import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public final class TenantContext {

	private final ThreadLocal<String> currentDatabaseKey = new ThreadLocal<>();

	public Scope open(String databaseKey) {
		if (databaseKey == null || databaseKey.isBlank()) {
			throw new IllegalArgumentException("Tenant database key is required");
		}
		if (currentDatabaseKey.get() != null) {
			throw new IllegalStateException("A tenant context is already active");
		}
		currentDatabaseKey.set(databaseKey);
		return new Scope(Thread.currentThread());
	}

	public Optional<String> currentDatabaseKey() {
		return Optional.ofNullable(currentDatabaseKey.get());
	}

	public final class Scope implements AutoCloseable {

		private final Thread owner;
		private boolean closed;

		private Scope(Thread owner) {
			this.owner = owner;
		}

		@Override
		public void close() {
			if (!closed) {
				if (Thread.currentThread() != owner) {
					throw new IllegalStateException(
						"A tenant context must be closed by the thread that opened it");
				}
				currentDatabaseKey.remove();
				closed = true;
			}
		}
	}
}
