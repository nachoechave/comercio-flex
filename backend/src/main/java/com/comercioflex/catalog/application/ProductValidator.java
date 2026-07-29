package com.comercioflex.catalog.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class ProductValidator {

	private static final Pattern SKU_PATTERN =
		Pattern.compile("[A-Z0-9][A-Z0-9._-]{0,63}");
	private static final Pattern PRICE_PATTERN =
		Pattern.compile("(?:0|[1-9][0-9]{0,12})(?:\\.[0-9]{1,2})?");

	public String name(String raw) {
		return text(raw, 2, 160, "El nombre");
	}

	public String description(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String value = raw.strip();
		if (value.length() > 2000 || containsControl(value)) {
			throw new InvalidProductException(
				"La descripción admite hasta 2000 caracteres válidos.");
		}
		return value;
	}

	public VariantValues variant(RawVariantValues raw) {
		if (raw == null) {
			throw new InvalidProductException("La variante es obligatoria.");
		}
		String sku = raw.sku() == null
			? ""
			: raw.sku().strip().toUpperCase(Locale.ROOT);
		if (!SKU_PATTERN.matcher(sku).matches()) {
			throw new InvalidProductException(
				"El SKU debe usar 1 a 64 letras, números, punto, guion o guion bajo.");
		}
		if (raw.price() == null || !PRICE_PATTERN.matcher(raw.price()).matches()) {
			throw new InvalidProductException(
				"El precio debe ser un string decimal positivo con hasta dos decimales.");
		}
		BigDecimal price;
		try {
			price = new BigDecimal(raw.price()).setScale(2, RoundingMode.UNNECESSARY);
		}
		catch (ArithmeticException | NumberFormatException exception) {
			throw new InvalidProductException("El precio no tiene un formato válido.");
		}
		if (price.signum() <= 0 || price.precision() > 15) {
			throw new InvalidProductException(
				"El precio debe ser mayor que cero y caber en DECIMAL(15,2).");
		}
		return new VariantValues(
			sku,
			price,
			option(raw.size(), "El talle"),
			option(raw.color(), "El color"));
	}

	public List<VariantValues> variants(List<RawVariantValues> rawVariants) {
		if (rawVariants == null || rawVariants.isEmpty()) {
			throw new InvalidProductException(
				"El producto debe tener al menos una variante.");
		}
		List<VariantValues> values = rawVariants.stream().map(this::variant).toList();
		Set<String> skus = new HashSet<>();
		Set<String> combinations = new HashSet<>();
		for (VariantValues variant : values) {
			if (!skus.add(variant.sku())) {
				throw new ProductConflictException("El SKU está repetido en el producto.");
			}
			String combination = variant.size().toLowerCase(Locale.ROOT)
				+ '\u0000'
				+ variant.color().toLowerCase(Locale.ROOT);
			if (!combinations.add(combination)) {
				throw new ProductConflictException(
					"La combinación de talle y color está repetida.");
			}
		}
		return values;
	}

	public String query(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String value = raw.strip();
		if (value.length() > 100 || containsControl(value)) {
			throw new InvalidProductException(
				"La búsqueda admite hasta 100 caracteres válidos.");
		}
		return value;
	}

	private String option(String raw, String field) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		return text(raw, 1, 60, field);
	}

	private String text(String raw, int min, int max, String field) {
		if (raw == null) {
			throw new InvalidProductException(field + " es obligatorio.");
		}
		String value = raw.strip().replaceAll("\\s+", " ");
		if (value.length() < min || value.length() > max || containsControl(value)) {
			throw new InvalidProductException(
				field + " debe tener entre " + min + " y " + max + " caracteres válidos.");
		}
		return value;
	}

	private boolean containsControl(String value) {
		return value.codePoints().anyMatch(Character::isISOControl);
	}
}
