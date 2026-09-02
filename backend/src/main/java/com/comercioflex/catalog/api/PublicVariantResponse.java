package com.comercioflex.catalog.api;

import java.util.List;

import com.comercioflex.catalog.domain.PublicVariant;

public record PublicVariantResponse(
	String id,
	String price,
	String size,
	String color,
	List<VariantOptionValueResponse> options,
	boolean available,
	String availableQuantity) {

	static PublicVariantResponse from(PublicVariant variant) {
		return new PublicVariantResponse(
			variant.id().toString(),
			variant.price().setScale(2).toPlainString(),
			variant.size(),
			variant.color(),
			variant.options().stream().map(VariantOptionValueResponse::from).toList(),
			variant.available(),
			variant.availableQuantity().stripTrailingZeros().toPlainString());
	}
}