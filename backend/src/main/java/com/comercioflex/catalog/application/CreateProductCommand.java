package com.comercioflex.catalog.application;

import java.util.List;
import java.util.UUID;

public record CreateProductCommand(
	String name,
	String description,
	UUID categoryId,
	List<RawVariantValues> variants) {
}
