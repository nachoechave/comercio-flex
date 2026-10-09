package com.comercioflex.catalog.domain;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.comercioflex.media.domain.ProductImageReference;

public record Product(
	UUID id,
	String name,
	String slug,
	String description,
	ProductStatus status,
	ProductCategory category,
	ProductImageReference image,
	List<ProductVariant> variants,
	long version,
	Instant createdAt,
	Instant updatedAt, List<ProductImageReference> images,
    SaleUnit saleUnit, BigDecimal saleMinimum, BigDecimal saleStep, BigDecimal saleMaximum) {
    public Product(UUID id, String name, String slug, String description, ProductStatus status,
        ProductCategory category, ProductImageReference image, List<ProductVariant> variants,
        long version, Instant createdAt, Instant updatedAt, List<ProductImageReference> images) {
        this(id, name, slug, description, status, category, image, variants, version, createdAt,
            updatedAt, images, SaleUnit.UNIT, BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("99"));
    }
	public Product(UUID id, String name, String slug, String description, ProductStatus status, ProductCategory category, ProductImageReference image, List<ProductVariant> variants, long version, Instant createdAt, Instant updatedAt) {
		this(id, name, slug, description, status, category, image, variants, version, createdAt, updatedAt, image == null ? List.of() : List.of(image));
	}
}
