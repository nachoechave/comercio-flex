package com.comercioflex.catalog.application;

import org.springframework.stereotype.Component;

@Component
public class CategoryNameNormalizer {

	public String normalize(String name) {
		if (name == null) {
			throw new InvalidCategoryNameException("El nombre es obligatorio.");
		}
		String normalized = name.strip().replaceAll("\\s+", " ");
		if (normalized.length() < 2 || normalized.length() > 120) {
			throw new InvalidCategoryNameException(
				"El nombre debe tener entre 2 y 120 caracteres.");
		}
		if (normalized.codePoints().anyMatch(Character::isISOControl)) {
			throw new InvalidCategoryNameException(
				"El nombre no puede contener caracteres de control.");
		}
		return normalized;
	}
}
