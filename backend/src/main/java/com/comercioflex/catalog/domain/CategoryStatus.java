package com.comercioflex.catalog.domain;

public enum CategoryStatus {
	ACTIVE,
	INACTIVE;

	public static CategoryStatus fromActive(boolean active) {
		return active ? ACTIVE : INACTIVE;
	}

	public boolean isActive() {
		return this == ACTIVE;
	}
}
