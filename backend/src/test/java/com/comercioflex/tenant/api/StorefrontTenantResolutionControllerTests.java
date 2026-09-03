package com.comercioflex.tenant.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.comercioflex.tenant.application.ResolvedTenant;
import com.comercioflex.tenant.application.TenantDomainResolver;

import jakarta.servlet.http.HttpServletRequest;

class StorefrontTenantResolutionControllerTests {

        private final TenantDomainResolver tenantDomainResolver =
                mock(TenantDomainResolver.class);

        private final StorefrontTenantResolutionController controller =
                new StorefrontTenantResolutionController(tenantDomainResolver);

        @Test
        void resolvesStoreFromRequestHostname() {
                HttpServletRequest request = mock(HttpServletRequest.class);

                ResolvedTenant tenant = new ResolvedTenant(
                        1L,
                        "la-ola-madre",
                        "La Ola Madre",
                        "tenant-la-ola-madre");

                when(request.getServerName())
                        .thenReturn("laolamadre.com.ar");

                when(tenantDomainResolver.resolveActive("laolamadre.com.ar"))
                        .thenReturn(tenant);

                StorefrontTenantResolutionResponse response =
                        controller.resolve(request);

                assertThat(response.storeSlug())
                        .isEqualTo("la-ola-madre");

                assertThat(response.displayName())
                        .isEqualTo("La Ola Madre");

                verify(tenantDomainResolver)
                        .resolveActive("laolamadre.com.ar");
        }
}