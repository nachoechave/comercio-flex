package com.comercioflex.catalog.application;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.catalog.domain.Category;
import com.comercioflex.catalog.domain.CategoryStatus;

@Service
public class CategoryService {

	private final CategoryRepository categoryRepository;
	private final CategoryNameNormalizer nameNormalizer;
	private final CategorySlugGenerator slugGenerator;
	private final TransactionTemplate transactionTemplate;

	public CategoryService(
			CategoryRepository categoryRepository,
			CategoryNameNormalizer nameNormalizer,
			CategorySlugGenerator slugGenerator,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate transactionTemplate) {
		this.categoryRepository = categoryRepository;
		this.nameNormalizer = nameNormalizer;
		this.slugGenerator = slugGenerator;
		this.transactionTemplate = transactionTemplate;
	}

	public List<Category> findAll(CategoryStatusFilter status) {
		return transactionTemplate.execute(ignored -> categoryRepository.findAll(status));
	}

	public Category findById(UUID id) {
		return transactionTemplate.execute(ignored -> requireCategory(id));
	}

	public Category create(String rawName) {
		String name = nameNormalizer.normalize(rawName);
		String slug = slugGenerator.generate(name);
		UUID id = UUID.randomUUID();
		return transactionTemplate.execute(ignored -> {
			categoryRepository.insert(id, name, slug, CategoryStatus.ACTIVE);
			return requireCategory(id);
		});
	}

	public Category rename(UUID id, String rawName) {
		String name = nameNormalizer.normalize(rawName);
		return transactionTemplate.execute(ignored -> {
			if (!categoryRepository.rename(id, name)) {
				throw new CategoryNotFoundException();
			}
			return requireCategory(id);
		});
	}

	public Category changeStatus(UUID id, boolean active) {
		return transactionTemplate.execute(ignored -> {
			if (!categoryRepository.changeStatus(id, CategoryStatus.fromActive(active))
					&& categoryRepository.findById(id).isEmpty()) {
				throw new CategoryNotFoundException();
			}
			return requireCategory(id);
		});
	}

	private Category requireCategory(UUID id) {
		return categoryRepository.findById(id)
			.orElseThrow(CategoryNotFoundException::new);
	}
}
