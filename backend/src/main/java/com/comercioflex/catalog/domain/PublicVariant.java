package com.comercioflex.catalog.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record PublicVariant(
	UUID id,
	BigDecimal price,
	String size,
	String color,
	boolean available) {
}
