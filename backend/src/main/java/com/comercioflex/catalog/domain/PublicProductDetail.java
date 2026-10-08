package com.comercioflex.catalog.domain;

import java.util.List;
import java.math.BigDecimal;
import java.util.UUID;

import com.comercioflex.media.domain.ProductImageReference;

public record PublicProductDetail(
	UUID id,
	String name,
	String slug,
	String description,
	PublicCategory category,
	ProductImageReference image,
	List<PublicVariant> variants, List<ProductImageReference> images,
    SaleUnit saleUnit, BigDecimal saleMinimum, BigDecimal saleStep, BigDecimal saleMaximum) {
    public PublicProductDetail(UUID id, String name, String slug, String description,
        PublicCategory category, ProductImageReference image, List<PublicVariant> variants,
        List<ProductImageReference> images) {
        this(id, name, slug, description, category, image, variants, images,
            SaleUnit.UNIT, BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("99"));
    }
	public PublicProductDetail(UUID id, String name, String slug, String description, PublicCategory category, ProductImageReference image, List<PublicVariant> variants) {
		this(id, name, slug, description, category, image, variants, image == null ? List.of() : List.of(image));
	}
}
