package com.comercioflex.media.domain;

import java.time.Instant;
import java.util.UUID;

public record ProductImage(
	UUID id,
	UUID productId,
	String displayStorageKey,
	String thumbnailStorageKey,
	String contentType,
	long displayByteSize,
	long thumbnailByteSize,
	int width,
	int height,
	String altText,
	String sha256,
	long version,
	Instant updatedAt, int position, boolean primaryImage) {
	public ProductImage(UUID id, UUID productId, String displayStorageKey, String thumbnailStorageKey,
		String contentType, long displayByteSize, long thumbnailByteSize, int width, int height,
		String altText, String sha256, long version, Instant updatedAt) {
		this(id, productId, displayStorageKey, thumbnailStorageKey, contentType, displayByteSize,
			thumbnailByteSize, width, height, altText, sha256, version, updatedAt, 0, true);
	}
}
