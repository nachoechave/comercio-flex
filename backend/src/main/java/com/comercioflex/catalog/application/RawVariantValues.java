package com.comercioflex.catalog.application;

import java.util.List;

public record RawVariantValues(
	String sku,
	String price,
	String size,
	String color,
	List<RawVariantOptionValue> options) {

	public RawVariantValues(String sku, String price, String size, String color) {
		this(sku, price, size, color, List.of());
	}
}
