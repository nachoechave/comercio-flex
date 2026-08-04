package com.comercioflex.media.application;

public record ProcessedProductImage(
	byte[] displayBytes,
	byte[] thumbnailBytes,
	String contentType,
	String extension,
	int width,
	int height,
	String sha256) {
}
