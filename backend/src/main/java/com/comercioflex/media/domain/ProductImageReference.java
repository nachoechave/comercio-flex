package com.comercioflex.media.domain;

import java.util.UUID;

public record ProductImageReference(UUID id, String altText, int position, boolean primaryImage) {
	public ProductImageReference(UUID id, String altText) { this(id, altText, 0, true); }
}
