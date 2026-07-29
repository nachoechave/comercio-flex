package com.comercioflex.catalog.api;

import java.time.Instant;
import java.util.List;

import com.comercioflex.catalog.domain.Product;
import com.comercioflex.catalog.domain.ProductStatus;

public record ProductDetailResponse(
	String id,
	String name,
	String slug,
	String description,
	ProductStatus status,
	ProductCategoryResponse category,
	List<ProductVariantResponse> variants,
	long version,
	Instant createdAt,
	Instant updatedAt) {

	static ProductDetailResponse from(Product product) {
		return new ProductDetailResponse(
			product.id().toString(),
			product.name(),
			product.slug(),
			product.description(),
			product.status(),
			ProductCategoryResponse.from(product.category()),
			product.variants().stream().map(ProductVariantResponse::from).toList(),
			product.version(),
			product.createdAt(),
			product.updatedAt());
	}
}
