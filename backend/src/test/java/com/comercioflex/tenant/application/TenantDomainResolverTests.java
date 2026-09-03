package com.comercioflex.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.comercioflex.tenant.infrastructure.control.TenantDomainRepository;

class TenantDomainResolverTests {

        private final TenantDomainRepository tenantDomainRepository =
                mock(TenantDomainRepository.class);

        private final TenantResolver tenantResolver =
                mock(TenantResolver.class);

        private final TenantDomainResolver resolver =
                new TenantDomainResolver(tenantDomainRepository, tenantResolver);

        @Test
        void resolvesUppercaseHostnameWithTrailingDot() {
                ResolvedTenant expected = mock(ResolvedTenant.class);

                when(tenantDomainRepository.findActiveSlugByHostname("laolamadre.com.ar"))
                        .thenReturn(Optional.of("la-ola-madre"));

                when(tenantResolver.resolveActive("la-ola-madre"))
                        .thenReturn(expected);

                ResolvedTenant result = resolver.resolveActive("LAOLAMADRE.COM.AR.");

                assertThat(result).isSameAs(expected);

                verify(tenantDomainRepository)
                        .findActiveSlugByHostname("laolamadre.com.ar");

                verify(tenantResolver)
                        .resolveActive("la-ola-madre");
        }

        @Test
        void rejectsUnknownHostname() {
                when(tenantDomainRepository.findActiveSlugByHostname("desconocida.com.ar"))
                        .thenReturn(Optional.empty());

                assertThatThrownBy(() -> resolver.resolveActive("desconocida.com.ar"))
                        .isInstanceOf(TenantNotFoundException.class);
        }

        @Test
        void rejectsBlankHostname() {
                assertThatThrownBy(() -> resolver.resolveActive(" "))
                        .isInstanceOf(TenantNotFoundException.class);
        }

        @Test
        void rejectsNullHostname() {
                assertThatThrownBy(() -> resolver.resolveActive(null))
                        .isInstanceOf(TenantNotFoundException.class);
        }
}