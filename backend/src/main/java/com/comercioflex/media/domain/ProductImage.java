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
	Instant updatedAt) {
}
