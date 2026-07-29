package com.comercioflex.catalog.application;

import java.util.List;
import java.util.Optional;

import com.comercioflex.catalog.domain.PublicCategory;
import com.comercioflex.catalog.domain.PublicProductDetail;

public interface PublicCatalogRepository {

	List<PublicCategory> findVisibleCategories();

	PublicProductPage findProducts(PublicCatalogSearch search);

	Optional<PublicProductDetail> findProductBySlug(String productSlug);
}
