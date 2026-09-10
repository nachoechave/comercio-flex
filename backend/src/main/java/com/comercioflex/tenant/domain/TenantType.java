package com.comercioflex.tenant.domain;

/** Functional experience, independent of industry and storefront presentation. */
public enum TenantType {

	ECOMMERCE,
	RADIO;

	@com.fasterxml.jackson.annotation.JsonCreator
	public static TenantType fromJson(String value) {
		return TenantType.valueOf(value);
	}
}
