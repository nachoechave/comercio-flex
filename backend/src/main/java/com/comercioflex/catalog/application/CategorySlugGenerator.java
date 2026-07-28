package com.comercioflex.catalog.application;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class CategorySlugGenerator {

	public String generate(String name) {
		String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD);
		String slug = decomposed
			.replaceAll("\\p{M}+", "")
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "-")
			.replaceAll("^-|-$", "");
		if (slug.isEmpty()) {
			throw new InvalidCategoryNameException(
				"El nombre debe contener letras o números que permitan generar una dirección.");
		}
		return slug;
	}
}
