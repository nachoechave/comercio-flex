package com.comercioflex.notification.infrastructure;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.comercioflex.tenant.application.TenantContext;
import com.comercioflex.tenant.domain.BrandAssetReference;
import com.comercioflex.tenant.domain.StoreSettings;
import com.comercioflex.tenant.infrastructure.control.TenantRepository;

@Component
class EmailBrandingResolver {
	private static final String DEFAULT_STORE_NAME = "Comercio Flex";
	private static final String DEFAULT_COLOR = "#334155";
	private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
	private static final Pattern SAFE_SLUG = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,98}[a-z0-9])?");
	private static final Pattern ETAG = Pattern.compile("[0-9a-fA-F]{64}");

	private final TenantContext tenantContext;
	private final TenantRepository tenants;
	private final EmailProperties properties;

	EmailBrandingResolver(TenantContext tenantContext, TenantRepository tenants,
			EmailProperties properties) {
		this.tenantContext = tenantContext;
		this.tenants = tenants;
		this.properties = properties;
	}

	EmailBranding resolve(StoreSettings store) {
		String name = store.storeName() == null || store.storeName().isBlank()
			? DEFAULT_STORE_NAME : store.storeName().trim();
		String color = primaryColor(store);
		return new EmailBranding(name, color, contrastingText(color), logoUrl(store));
	}

	private String primaryColor(StoreSettings store) {
		String candidate = store.branding() == null ? null : store.branding().primaryColor();
		return candidate != null && COLOR.matcher(candidate).matches()
			? candidate.toUpperCase(Locale.ROOT) : DEFAULT_COLOR;
	}

	private String contrastingText(String background) {
		int red = Integer.parseInt(background.substring(1, 3), 16);
		int green = Integer.parseInt(background.substring(3, 5), 16);
		int blue = Integer.parseInt(background.substring(5, 7), 16);
		double luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255;
		return luminance > 0.57 ? "#172033" : "#FFFFFF";
	}

	private String logoUrl(StoreSettings store) {
		BrandAssetReference logo = store.branding() == null ? null : store.branding().logo();
		if (logo == null || logo.etag() == null || !ETAG.matcher(logo.etag()).matches()) return null;
		URI base = publicHttpsBase();
		if (base == null) return null;
		return tenantContext.currentDatabaseKey()
			.flatMap(tenants::findActiveByDatabaseKey)
			.filter(tenant -> SAFE_SLUG.matcher(tenant.slug()).matches())
			.map(tenant -> base.resolve("/api/v1/stores/" + tenant.slug()
				+ "/media/branding/logo?v=" + logo.etag()).toString())
			.orElse(null);
	}

	private URI publicHttpsBase() {
		try {
			URI uri = URI.create(properties.getPublicBaseUri());
			String host = uri.getHost();
			if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
					|| uri.getUserInfo() != null || internalHost(host)) return null;
			return uri;
		}
		catch (RuntimeException exception) {
			return null;
		}
	}

	private boolean internalHost(String host) {
		String value = host.toLowerCase(Locale.ROOT);
		if (value.equals("localhost") || value.endsWith(".localhost")
				|| value.endsWith(".local") || value.endsWith(".internal")
				|| value.contains(":")) return true;
		if (!value.matches("\\d{1,3}(?:\\.\\d{1,3}){3}")) return false;
		String[] octets = value.split("\\.");
		try {
			int first = Integer.parseInt(octets[0]);
			int second = Integer.parseInt(octets[1]);
			for (String octet : octets) if (Integer.parseInt(octet) > 255) return true;
			return first == 10 || first == 127 || first == 0
				|| (first == 169 && second == 254)
				|| (first == 172 && second >= 16 && second <= 31)
				|| (first == 192 && second == 168);
		}
		catch (NumberFormatException exception) {
			return true;
		}
	}
}
