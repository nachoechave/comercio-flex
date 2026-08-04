package com.comercioflex.media.api;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.media.domain.ProductImage;
import com.comercioflex.media.domain.ProductImageReference;

public record ProductImageResponse(
	String id,
	String url,
	String thumbnailUrl,
	String altText,
	Integer width,
	Integer height,
	Instant updatedAt) {

	public static ProductImageResponse admin(String storeSlug, ProductImage image) {
		String base = adminBase(storeSlug, image.id());
		return new ProductImageResponse(image.id().toString(), base + "/display",
			base + "/thumbnail", image.altText(), image.width(), image.height(), image.updatedAt());
	}

	public static ProductImageResponse admin(String storeSlug, ProductImageReference image) {
		String base = adminBase(storeSlug, image.id());
		return new ProductImageResponse(image.id().toString(), base + "/display",
			base + "/thumbnail", image.altText(), null, null, null);
	}

	public static ProductImageResponse publicView(String storeSlug, ProductImageReference image) {
		String base = "/api/v1/stores/" + storeSlug + "/media/product-images/" + image.id();
		return new ProductImageResponse(image.id().toString(), base + "/display",
			base + "/thumbnail", image.altText(), null, null, null);
	}

	private static String adminBase(String storeSlug, UUID imageId) {
		return "/api/v1/stores/" + storeSlug + "/admin/product-images/" + imageId;
	}
}
