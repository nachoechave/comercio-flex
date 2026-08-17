package com.comercioflex.catalog.application;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.catalog.domain.Product;
import com.comercioflex.catalog.domain.ProductStatus;
import com.comercioflex.catalog.domain.ProductVariant;

@Service
public class ProductService {

	private final ProductRepository repository;
	private final ProductValidator validator;
	private final CategorySlugGenerator slugGenerator;
	private final TransactionTemplate transactionTemplate;

	public ProductService(
			ProductRepository repository,
			ProductValidator validator,
			CategorySlugGenerator slugGenerator,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate transactionTemplate) {
		this.repository = repository;
		this.validator = validator;
		this.slugGenerator = slugGenerator;
		this.transactionTemplate = transactionTemplate;
	}

	public ProductPage findPage(ProductSearch search) {
		ProductSearch normalized = new ProductSearch(
			search.page(),
			search.size(),
			search.status(),
			search.categoryId(),
			validator.query(search.query()));
		return transactionTemplate.execute(ignored -> repository.findPage(normalized));
	}

	public Product findById(UUID productId) {
		return transactionTemplate.execute(ignored -> requireProduct(productId));
	}

	public Product create(CreateProductCommand command) {
		String name = validator.name(command.name());
		String description = validator.description(command.description());
		List<VariantValues> variants = validator.variants(command.variants());
		String slug = slugGenerator.generate(name);
		UUID productId = UUID.randomUUID();
		return transactionTemplate.execute(ignored -> {
			long categoryId = repository.lockActiveCategory(command.categoryId())
				.orElseThrow(() -> new ProductConflictException(
					"La categoría debe existir y estar activa."));
			long internalId = repository.insertProduct(
				productId,
				categoryId,
				name,
				slug,
				description);
			for (VariantValues variant : variants) {
				repository.insertVariant(UUID.randomUUID(), internalId, variant);
			}
			return requireProduct(productId);
		});
	}

	public Product update(
			UUID productId,
			String rawName,
			String rawDescription,
			UUID categoryId,
			long version) {
		String name = validator.name(rawName);
		String description = validator.description(rawDescription);
		return transactionTemplate.execute(ignored -> {
			LockedProduct product = lockProduct(productId);
			requireVersion(product.version(), version);
			requireEditable(product);
			long categoryInternalId = repository.lockActiveCategory(categoryId)
				.orElseThrow(() -> new ProductConflictException(
					"La categoría debe existir y estar activa."));
			if (!repository.updateProduct(
					product.internalId(),
					categoryInternalId,
					name,
					description,
					version)) {
				throw new StaleProductVersionException();
			}
			return requireProduct(productId);
		});
	}

	public Product changeStatus(UUID productId, ProductStatus target, long version) {
		return transactionTemplate.execute(ignored -> {
			LockedProduct product = lockProduct(productId);
			requireVersion(product.version(), version);
			if (!product.status().canTransitionTo(target)) {
				throw new ProductConflictException(
					"La transición de estado solicitada no está permitida.");
			}
			if (target == ProductStatus.PUBLISHED) {
				if (!repository.lockCategoryIsActive(product.categoryInternalId())) {
					throw new ProductConflictException(
						"No se puede publicar con una categoría inactiva.");
				}
				if (repository.countActiveVariants(product.internalId()) < 1) {
					throw new ProductConflictException(
						"El producto necesita al menos una variante activa.");
				}
			}
			if (target != product.status()
					&& !repository.updateProductStatus(
						product.internalId(),
						target,
						version)) {
				throw new StaleProductVersionException();
			}
			return requireProduct(productId);
		});
	}

	public ProductVariant addVariant(UUID productId, RawVariantValues raw) {
		VariantValues values = validator.variant(raw);
		UUID variantId = UUID.randomUUID();
		return transactionTemplate.execute(ignored -> {
			LockedProduct product = lockProduct(productId);
			requireEditable(product);
			repository.insertVariant(variantId, product.internalId(), values);
			return requireVariant(productId, variantId);
		});
	}

	public ProductVariant updateVariant(
			UUID productId,
			UUID variantId,
			RawVariantValues raw,
			long version) {
		VariantValues values = validator.variant(raw);
		return transactionTemplate.execute(ignored -> {
			LockedProduct product = lockProduct(productId);
			requireEditable(product);
			LockedVariant variant = lockVariant(product.internalId(), variantId);
			requireVersion(variant.version(), version);
			if (!repository.updateVariant(
					variant.internalId(), product.internalId(), values, version)) {
				throw new StaleProductVersionException();
			}
			return requireVariant(productId, variantId);
		});
	}

	public ProductVariant changeVariantStatus(
			UUID productId,
			UUID variantId,
			boolean active,
			long version) {
		return transactionTemplate.execute(ignored -> {
			LockedProduct product = lockProduct(productId);
			requireEditable(product);
			LockedVariant variant = lockVariant(product.internalId(), variantId);
			requireVersion(variant.version(), version);
			if (!active
					&& variant.active()
					&& product.status() == ProductStatus.PUBLISHED
					&& repository.countActiveVariants(product.internalId()) <= 1) {
				throw new ProductConflictException(
					"No se puede desactivar la última variante de un producto publicado.");
			}
			if (active != variant.active()
					&& !repository.updateVariantStatus(variant.internalId(), active, version)) {
				throw new StaleProductVersionException();
			}
			return requireVariant(productId, variantId);
		});
	}

	private Product requireProduct(UUID id) {
		return repository.findById(id).orElseThrow(ProductNotFoundException::new);
	}

	private ProductVariant requireVariant(UUID productId, UUID variantId) {
		return repository.findVariant(productId, variantId)
			.orElseThrow(ProductVariantNotFoundException::new);
	}

	private LockedProduct lockProduct(UUID id) {
		return repository.lockProduct(id).orElseThrow(ProductNotFoundException::new);
	}

	private LockedVariant lockVariant(long productId, UUID id) {
		return repository.lockVariant(productId, id)
			.orElseThrow(ProductVariantNotFoundException::new);
	}

	private void requireVersion(long current, long requested) {
		if (current != requested) {
			throw new StaleProductVersionException();
		}
	}

	private void requireEditable(LockedProduct product) {
		if (product.status() == ProductStatus.ARCHIVED) {
			throw new ProductConflictException(
				"Un producto archivado debe restaurarse a borrador antes de editarlo.");
		}
	}
}
