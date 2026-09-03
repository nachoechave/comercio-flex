package com.comercioflex.tenant.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.tenant.application.ResolvedTenant;
import com.comercioflex.tenant.application.TenantDomainResolver;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/storefront")
public class StorefrontTenantResolutionController {

        private final TenantDomainResolver tenantDomainResolver;

        public StorefrontTenantResolutionController(
                        TenantDomainResolver tenantDomainResolver) {
                this.tenantDomainResolver = tenantDomainResolver;
        }

        @GetMapping("/resolve")
        StorefrontTenantResolutionResponse resolve(HttpServletRequest request) {
                ResolvedTenant tenant =
                        tenantDomainResolver.resolveActive(request.getServerName());

                return StorefrontTenantResolutionResponse.from(tenant);
        }
}