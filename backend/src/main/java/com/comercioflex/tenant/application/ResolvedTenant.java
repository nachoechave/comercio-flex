package com.comercioflex.tenant.application;

public record ResolvedTenant(
	Long id,
	String slug,
	String displayName,
	String databaseKey) {
}
