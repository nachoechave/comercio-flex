package com.comercioflex.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CategoryNamingTests {

	private final CategoryNameNormalizer nameNormalizer = new CategoryNameNormalizer();
	private final CategorySlugGenerator slugGenerator = new CategorySlugGenerator();

	@Test
	void normalizesVisibleWhitespaceAndGeneratesAStableAsciiSlug() {
		String name = nameNormalizer.normalize("  Reméras   y   Niños  ");

		assertThat(name).isEqualTo("Reméras y Niños");
		assertThat(slugGenerator.generate(name)).isEqualTo("remeras-y-ninos");
	}

	@Test
	void rejectsNamesThatAreTooShortOrCannotGenerateASlug() {
		assertThatThrownBy(() -> nameNormalizer.normalize(" A "))
			.isInstanceOf(InvalidCategoryNameException.class);
		assertThatThrownBy(() -> slugGenerator.generate("日本"))
			.isInstanceOf(InvalidCategoryNameException.class);
	}

	@Test
	void rejectsControlCharacters() {
		assertThatThrownBy(() -> nameNormalizer.normalize("Reme\u0000ras"))
			.isInstanceOf(InvalidCategoryNameException.class);
	}
}
