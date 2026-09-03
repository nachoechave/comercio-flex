package com.comercioflex.tenant.api;

import com.comercioflex.tenant.application.ResolvedTenant;

public record StorefrontTenantResolutionResponse(
        String storeSlug,
        String displayName) {

        static StorefrontTenantResolutionResponse from(ResolvedTenant tenant) {
                return new StorefrontTenantResolutionResponse(
                        tenant.slug(),
                        tenant.displayName());
        }
}