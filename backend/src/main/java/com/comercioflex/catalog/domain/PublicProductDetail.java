package com.comercioflex.catalog.domain;

import java.util.List;
import java.util.UUID;

import com.comercioflex.media.domain.ProductImageReference;

public record PublicProductDetail(
	UUID id,
	String name,
	String slug,
	String description,
	PublicCategory category,
	ProductImageReference image,
	List<PublicVariant> variants) {
}
