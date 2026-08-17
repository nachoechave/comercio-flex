package com.comercioflex.catalog.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.comercioflex.catalog.domain.VariantOptionValue;

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
		List<VariantOptionValue> options = options(raw);
		return new VariantValues(
			sku,
			price,
			legacyValue(options, "talle"),
			legacyValue(options, "color"),
			options,
			optionSignature(options));
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
			if (!combinations.add(variant.optionSignature())) {
				throw new ProductConflictException(
					"La combinación de opciones está repetida.");
			}
		}
		return values;
	}

	private List<VariantOptionValue> options(RawVariantValues raw) {
		boolean hasGenericOptions = raw.options() != null && !raw.options().isEmpty();
		boolean hasLegacyOptions = hasText(raw.size()) || hasText(raw.color());
		if (hasGenericOptions && hasLegacyOptions) {
			throw new InvalidProductException(
				"Usá options o los campos compatibles size/color, pero no ambos.");
		}
		if (!hasGenericOptions) {
			List<VariantOptionValue> legacy = new ArrayList<>(2);
			if (hasText(raw.size())) {
				legacy.add(new VariantOptionValue("Talle", option(raw.size(), "El talle")));
			}
			if (hasText(raw.color())) {
				legacy.add(new VariantOptionValue("Color", option(raw.color(), "El color")));
			}
			return List.copyOf(legacy);
		}
		if (raw.options().size() > 5) {
			throw new InvalidProductException("Cada variante admite hasta 5 opciones.");
		}
		Set<String> names = new HashSet<>();
		List<VariantOptionValue> values = new ArrayList<>(raw.options().size());
		for (RawVariantOptionValue rawOption : raw.options()) {
			if (rawOption == null) {
				throw new InvalidProductException("La opción de variante es obligatoria.");
			}
			String name = text(rawOption.name(), 1, 40, "El nombre de la opción");
			String normalizedName = normalize(name);
			if (!names.add(normalizedName)) {
				throw new InvalidProductException(
					"Los nombres de opción no pueden repetirse en una variante.");
			}
			values.add(new VariantOptionValue(
				name,
				text(rawOption.value(), 1, 60, "El valor de la opción")));
		}
		return List.copyOf(values);
	}

	private String legacyValue(List<VariantOptionValue> options, String name) {
		return options.stream()
			.filter(option -> normalize(option.name()).equals(name))
			.map(VariantOptionValue::value)
			.findFirst()
			.orElse("");
	}

	private String optionSignature(List<VariantOptionValue> options) {
		String canonical = options.stream()
			.sorted(Comparator.comparing(option -> normalize(option.name())))
			.map(option -> normalize(option.name()) + "=" + normalize(option.value()))
			.reduce((left, right) -> left + '\u001f' + right)
			.orElse("");
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(canonical.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 no está disponible.", exception);
		}
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private String normalize(String value) {
		return value.toLowerCase(Locale.ROOT);
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
