package com.comercioflex.tenant.application;

import java.net.IDN;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.comercioflex.tenant.infrastructure.control.TenantDomainRepository;

@Service
public class TenantDomainResolver {

        private final TenantDomainRepository tenantDomainRepository;
        private final TenantResolver tenantResolver;

        public TenantDomainResolver(
                        TenantDomainRepository tenantDomainRepository,
                        TenantResolver tenantResolver) {
                this.tenantDomainRepository = tenantDomainRepository;
                this.tenantResolver = tenantResolver;
        }

        @Transactional(readOnly = true)
        public ResolvedTenant resolveActive(String hostname) {
                String normalizedHostname = normalize(hostname);

                String slug = tenantDomainRepository.findActiveSlugByHostname(normalizedHostname)
                        .orElseThrow(TenantNotFoundException::new);

                return tenantResolver.resolveActive(slug);
        }

        private String normalize(String hostname) {
                if (hostname == null || hostname.isBlank()) {
                        throw new TenantNotFoundException();
                }

                String normalized = hostname.trim()
                        .toLowerCase(Locale.ROOT);

                if (normalized.endsWith(".")) {
                        normalized = normalized.substring(0, normalized.length() - 1);
                }

                try {
                        normalized = IDN.toASCII(normalized);
                }
                catch (IllegalArgumentException exception) {
                        throw new TenantNotFoundException();
                }

                if (normalized.isBlank() || normalized.length() > 253) {
                        throw new TenantNotFoundException();
                }

                return normalized;
        }
}