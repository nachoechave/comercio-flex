package com.comercioflex.catalog.application;

import java.util.Optional;
import java.util.UUID;

import com.comercioflex.catalog.domain.Product;
import com.comercioflex.catalog.domain.ProductStatus;
import com.comercioflex.catalog.domain.ProductVariant;

public interface ProductRepository {

	ProductPage findPage(ProductSearch search);

	Optional<Product> findById(UUID productId);

	Optional<ProductVariant> findVariant(UUID productId, UUID variantId);

	Optional<LockedProduct> lockProduct(UUID productId);

	Optional<LockedVariant> lockVariant(long productInternalId, UUID variantId);

	Optional<Long> lockActiveCategory(UUID categoryId);

	boolean lockCategoryIsActive(long categoryInternalId);

	long insertProduct(
		UUID publicId,
		long categoryInternalId,
		String name,
		String slug,
		String description);

	void insertVariant(
		UUID publicId,
		long productInternalId,
		VariantValues values);

	boolean updateProduct(
		long internalId,
		long categoryInternalId,
		String name,
		String description,
		long expectedVersion);

	boolean updateProductStatus(
		long internalId,
		ProductStatus status,
		long expectedVersion);

	boolean updateVariant(
		long internalId,
		VariantValues values,
		long expectedVersion);

	boolean updateVariantStatus(
		long internalId,
		boolean active,
		long expectedVersion);

	int countActiveVariants(long productInternalId);
}
