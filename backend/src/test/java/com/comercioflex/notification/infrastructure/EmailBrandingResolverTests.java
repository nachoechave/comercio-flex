package com.comercioflex.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.comercioflex.tenant.application.TenantContext;
import com.comercioflex.tenant.domain.BrandAssetReference;
import com.comercioflex.tenant.domain.BrandFont;
import com.comercioflex.tenant.domain.StoreSettings;
import com.comercioflex.tenant.domain.StorefrontTemplate;
import com.comercioflex.tenant.domain.TenantBranding;
import com.comercioflex.tenant.infrastructure.control.ActiveTenant;
import com.comercioflex.tenant.infrastructure.control.TenantRepository;

class EmailBrandingResolverTests {
	private static final String ETAG = "a".repeat(64);

	private final TenantContext context = mock(TenantContext.class);
	private final TenantRepository tenants = mock(TenantRepository.class);
	private final EmailProperties properties = new EmailProperties();
	private final EmailBrandingResolver resolver = new EmailBrandingResolver(
		context, tenants, properties);

	@Test
	void resolvesTheCurrentTenantNameColorAndPublicHttpsLogo() {
		properties.setPublicBaseUri("https://api.comercioflex.com.ar");
		when(context.currentDatabaseKey()).thenReturn(Optional.of("tenant_a"));
		when(tenants.findActiveByDatabaseKey("tenant_a")).thenReturn(Optional.of(
			new ActiveTenant(1L, "tienda-a", "Tienda A", "tenant_a")));

		EmailBranding result = resolver.resolve(store("Tienda Á", "#f5e942", true));

		assertThat(result.storeName()).isEqualTo("Tienda Á");
		assertThat(result.headerColor()).isEqualTo("#F5E942");
		assertThat(result.headerTextColor()).isEqualTo("#172033");
		assertThat(result.logoUrl()).isEqualTo(
			"https://api.comercioflex.com.ar/api/v1/stores/tienda-a/media/branding/logo?v=" + ETAG);
	}

	@Test
	void tenantDatabaseKeyScopesTheLogoAndNeverUsesAnotherTenantSlug() {
		properties.setPublicBaseUri("https://api.comercioflex.com.ar");
		when(context.currentDatabaseKey()).thenReturn(
			Optional.of("tenant_a"), Optional.of("tenant_b"));
		when(tenants.findActiveByDatabaseKey("tenant_a")).thenReturn(Optional.of(
			new ActiveTenant(1L, "tienda-a", "Tienda A", "tenant_a")));
		when(tenants.findActiveByDatabaseKey("tenant_b")).thenReturn(Optional.of(
			new ActiveTenant(2L, "tienda-b", "Tienda B", "tenant_b")));

		EmailBranding first = resolver.resolve(store("Tienda A", "#112233", true));
		EmailBranding second = resolver.resolve(store("Tienda B", "#445566", true));

		assertThat(first.logoUrl()).contains("/tienda-a/").doesNotContain("tienda-b");
		assertThat(second.logoUrl()).contains("/tienda-b/").doesNotContain("tienda-a");
		assertThat(first.headerColor()).isNotEqualTo(second.headerColor());
	}

	@Test
	void missingBrandingUsesSafeFallbacksWithoutABrokenImage() {
		EmailBranding result = resolver.resolve(store("  ", "not-a-color", false));

		assertThat(result.storeName()).isEqualTo("Comercio Flex");
		assertThat(result.headerColor()).isEqualTo("#334155");
		assertThat(result.headerTextColor()).isEqualTo("#FFFFFF");
		assertThat(result.logoUrl()).isNull();
	}

	@Test
	void logoIsOmittedForRelativeLocalPrivateOrInternalHosts() {
		when(context.currentDatabaseKey()).thenReturn(Optional.of("tenant_a"));
		when(tenants.findActiveByDatabaseKey("tenant_a")).thenReturn(Optional.of(
			new ActiveTenant(1L, "tienda-a", "Tienda A", "tenant_a")));

		for (String base : new String[] {"/api", "http://public.example.com",
				"https://localhost", "https://127.0.0.1", "https://10.0.0.2",
				"https://172.20.0.3", "https://192.168.1.2", "https://backend.internal"}) {
			properties.setPublicBaseUri(base);
			assertThat(resolver.resolve(store("Tienda A", "#123456", true)).logoUrl())
				.as(base).isNull();
		}
	}

	private StoreSettings store(String name, String color, boolean withLogo) {
		BrandAssetReference logo = withLogo
			? new BrandAssetReference("private/branding/logo.webp", "image/webp", ETAG) : null;
		TenantBranding branding = new TenantBranding(color, "#222222", "#FFFFFF", "#111111",
			BrandFont.SYSTEM, null, null, StorefrontTemplate.CATALOG, logo, null, null);
		return new StoreSettings(name, "ARS", "America/Argentina/Buenos_Aires",
			null, null, null, null, false, null, null, null, null, null, branding);
	}
}
