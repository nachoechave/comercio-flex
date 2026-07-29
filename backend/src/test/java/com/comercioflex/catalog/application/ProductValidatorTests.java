package com.comercioflex.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.comercioflex.catalog.domain.ProductStatus;

class ProductValidatorTests {

	private final ProductValidator validator = new ProductValidator();

	@Test
	void normalizesSkuPriceAndOptionalClothingValues() {
		VariantValues values = validator.variant(
			new RawVariantValues(" rem-001 ", "1500.5", " M ", " Azul "));

		assertThat(values.sku()).isEqualTo("REM-001");
		assertThat(values.price().toPlainString()).isEqualTo("1500.50");
		assertThat(values.size()).isEqualTo("M");
		assertThat(values.color()).isEqualTo("Azul");

		VariantValues base = validator.variant(
			new RawVariantValues("BASE", "1", null, " "));
		assertThat(base.size()).isEmpty();
		assertThat(base.color()).isEmpty();
	}

	@Test
	void rejectsInvalidPricesSkusAndDuplicateVariantCombinations() {
		assertThatThrownBy(() -> validator.variant(
			new RawVariantValues("sku con espacios", "10.00", null, null)))
			.isInstanceOf(InvalidProductException.class);
		assertThatThrownBy(() -> validator.variant(
			new RawVariantValues("SKU", "0", null, null)))
			.isInstanceOf(InvalidProductException.class);
		assertThatThrownBy(() -> validator.variant(
			new RawVariantValues("SKU", "1.234", null, null)))
			.isInstanceOf(InvalidProductException.class);

		assertThatThrownBy(() -> validator.variants(List.of(
			new RawVariantValues("SKU-1", "10", "M", "Azul"),
			new RawVariantValues("SKU-2", "20", "m", "azul"))))
			.isInstanceOf(ProductConflictException.class);
		assertThatThrownBy(() -> validator.variants(List.of(
			new RawVariantValues("sku-1", "10", "M", "Azul"),
			new RawVariantValues("SKU-1", "20", "L", "Rojo"))))
			.isInstanceOf(ProductConflictException.class);
	}

	@Test
	void requiresAtLeastOneVariantAndBoundsProductFields() {
		assertThatThrownBy(() -> validator.variants(List.of()))
			.isInstanceOf(InvalidProductException.class);
		assertThatThrownBy(() -> validator.name("A"))
			.isInstanceOf(InvalidProductException.class);
		assertThatThrownBy(() -> validator.description("x".repeat(2001)))
			.isInstanceOf(InvalidProductException.class);
	}

	@Test
	void enforcesTheApprovedPublicationStateMachine() {
		assertThat(ProductStatus.DRAFT.canTransitionTo(ProductStatus.PUBLISHED)).isTrue();
		assertThat(ProductStatus.PUBLISHED.canTransitionTo(ProductStatus.ARCHIVED)).isTrue();
		assertThat(ProductStatus.ARCHIVED.canTransitionTo(ProductStatus.DRAFT)).isTrue();
		assertThat(ProductStatus.ARCHIVED.canTransitionTo(ProductStatus.PUBLISHED)).isFalse();
	}
}
