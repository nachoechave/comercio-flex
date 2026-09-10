package com.comercioflex.tenant.api;

import com.comercioflex.tenant.application.ResolvedTenant;

import com.comercioflex.tenant.domain.TenantType;


public record StorefrontTenantResolutionResponse(
        String storeSlug,
        String displayName,
        TenantType tenantType) {

        static StorefrontTenantResolutionResponse from(ResolvedTenant tenant) {
                return new StorefrontTenantResolutionResponse(
                        tenant.slug(),
                        tenant.displayName(), tenant.tenantType());
        }
}
