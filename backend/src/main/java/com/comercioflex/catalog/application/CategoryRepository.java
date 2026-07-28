package com.comercioflex.catalog.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.comercioflex.catalog.domain.Category;
import com.comercioflex.catalog.domain.CategoryStatus;

public interface CategoryRepository {

	List<Category> findAll(CategoryStatusFilter status);

	Optional<Category> findById(UUID id);

	void insert(UUID id, String name, String slug, CategoryStatus status);

	boolean rename(UUID id, String name);

	boolean changeStatus(UUID id, CategoryStatus status);
}
