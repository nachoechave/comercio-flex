package com.comercioflex.media.application;

import java.util.Optional;
import java.util.UUID;

import com.comercioflex.media.domain.ProductImage;

public interface ProductImageRepository {
	Optional<LockedImageProduct> lockProduct(UUID productId);
	Optional<ProductImage> findByProductId(UUID productId);
	Optional<ProductImage> findByPublicId(UUID imageId, boolean requirePublishedProduct);
	Optional<ProductImage> upsert(long productInternalId, ProductImage image);
	Optional<ProductImage> delete(UUID productId);
}
