package com.comercioflex.tenant.domain;

import java.util.Locale;

public enum BrandAssetType {
	LOGO,
	FAVICON,
	HERO;

	public static BrandAssetType parse(String value) {
		try {
			return valueOf(value.toUpperCase(Locale.ROOT));
		}
		catch (RuntimeException exception) {
			throw new IllegalArgumentException("Unknown branding asset type");
		}
	}
}
