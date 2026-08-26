package com.comercioflex.catalog.domain;

import java.util.UUID;

import com.comercioflex.media.domain.ProductImageReference;

public record PublicCategory(
	UUID id,
	String name,
	String slug,
	ProductImageReference image) {
}
