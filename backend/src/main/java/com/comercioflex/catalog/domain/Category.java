package com.comercioflex.catalog.domain;

import java.time.Instant;
import java.util.UUID;

public record Category(
	UUID id,
	String name,
	String slug,
	CategoryStatus status,
	Instant createdAt,
	Instant updatedAt) {

	public boolean active() {
		return status.isActive();
	}
}
