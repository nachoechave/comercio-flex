package com.comercioflex.catalog.application;

public record LockedVariant(
	long internalId,
	boolean active,
	long version) {
}
