package com.comercioflex.tenant.infrastructure.control;

public record ActiveTenant(Long id, String slug, String displayName, String databaseKey) {
}
