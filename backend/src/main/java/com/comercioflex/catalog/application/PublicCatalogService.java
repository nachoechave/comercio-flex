package com.comercioflex.catalog.application;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.catalog.domain.PublicCategory;
import com.comercioflex.catalog.domain.PublicProductDetail;

@Service
public class PublicCatalogService {

	private final PublicCatalogRepository repository;
	private final TransactionTemplate transactionTemplate;

	public PublicCatalogService(
			PublicCatalogRepository repository,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate transactionTemplate) {
		this.repository = repository;
		this.transactionTemplate = transactionTemplate;
	}

	public List<PublicCategory> findCategories() {
		return transactionTemplate.execute(ignored -> repository.findVisibleCategories());
	}

	public PublicProductPage findProducts(PublicCatalogSearch search) {
		PublicCatalogSearch normalized = new PublicCatalogSearch(
			search.page(),
			search.size(),
			normalizeQuery(search.query()),
			normalizeCategory(search.categorySlug()));
		return transactionTemplate.execute(ignored -> repository.findProducts(normalized));
	}

	public PublicProductDetail findProduct(String productSlug) {
		return transactionTemplate.execute(ignored ->
			repository.findProductBySlug(productSlug)
				.orElseThrow(ProductNotFoundException::new));
	}

	private String normalizeQuery(String query) {
		if (query == null || query.isBlank()) {
			return null;
		}
		return query.trim().replaceAll("\\s+", " ");
	}

	private String normalizeCategory(String categorySlug) {
		if (categorySlug == null || categorySlug.isBlank()) {
			return null;
		}
		return categorySlug;
	}
}
