package com.comercioflex.catalog.domain;

import java.util.List;
import java.util.UUID;

public record PublicProductDetail(
	UUID id,
	String name,
	String slug,
	String description,
	PublicCategory category,
	List<PublicVariant> variants) {
}
