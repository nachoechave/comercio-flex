package com.comercioflex.media.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.comercioflex.media.domain.ProductImage;

public interface ProductImageRepository {
	List<ProductImage> findAll(UUID productId);
	void insert(long productInternalId, ProductImage image, int position, boolean primary);
	void deleteImage(UUID productId, UUID imageId);
	void arrange(UUID productId, List<UUID> ids, UUID primaryId);
	Optional<LockedImageProduct> lockProduct(UUID productId);
	Optional<ProductImage> findByProductId(UUID productId);
	Optional<ProductImage> findByPublicId(UUID imageId, boolean requirePublishedProduct);
	Optional<ProductImage> upsert(long productInternalId, ProductImage image);
	Optional<ProductImage> delete(UUID productId);
}
