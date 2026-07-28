package com.comercioflex.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailNormalizerTests {

	private final EmailNormalizer normalizer = new EmailNormalizer();

	@Test
	void trimsAndLowercasesEmailUsingAStableRule() {
		assertThat(normalizer.normalize("  Owner@Example.COM "))
			.isEqualTo("owner@example.com");
	}

	@Test
	void treatsNullAsEmptyInput() {
		assertThat(normalizer.normalize(null)).isEmpty();
	}
}
