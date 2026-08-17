package com.comercioflex.catalog.infrastructure.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.comercioflex.catalog.application.LockedProduct;
import com.comercioflex.catalog.application.LockedVariant;
import com.comercioflex.catalog.application.ProductConflictException;
import com.comercioflex.catalog.application.ProductPage;
import com.comercioflex.catalog.application.ProductRepository;
import com.comercioflex.catalog.application.ProductSearch;
import com.comercioflex.catalog.application.ProductStatusFilter;
import com.comercioflex.catalog.application.VariantValues;
import com.comercioflex.catalog.domain.Product;
import com.comercioflex.catalog.domain.ProductCategory;
import com.comercioflex.catalog.domain.ProductStatus;
import com.comercioflex.catalog.domain.ProductSummary;
import com.comercioflex.catalog.domain.ProductVariant;
import com.comercioflex.catalog.domain.VariantOptionValue;
import com.comercioflex.media.domain.ProductImageReference;

@Repository
public class JdbcProductRepository implements ProductRepository {

	private static final String PRODUCT_HEADER = """
		SELECT
			BIN_TO_UUID(product.public_id) product_public_id,
			product.name,
			product.slug,
			product.description,
			product.status,
			product.version,
			product.created_at,
			product.updated_at,
			BIN_TO_UUID(category.public_id) category_public_id,
			category.name category_name,
			category.status category_status,
			BIN_TO_UUID(image.public_id) image_public_id,
			image.alt_text image_alt_text
		FROM products product
		JOIN categories category ON category.id = product.category_id
		LEFT JOIN product_images image ON image.product_id = product.id
		""";

	private static final String VARIANT_COLUMNS = """
		SELECT
			BIN_TO_UUID(variant.public_id) variant_public_id,
			variant.sku,
			variant.price,
			variant.size_value,
			variant.color_value,
			variant.status,
			variant.version,
			variant.created_at,
			variant.updated_at
		FROM product_variants variant
		""";

	private final JdbcTemplate jdbcTemplate;
	private final RowMapper<ProductVariant> variantMapper = this::mapVariant;

	public JdbcProductRepository(
			@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public ProductPage findPage(ProductSearch search) {
		StringBuilder where = new StringBuilder(" WHERE 1=1");
		List<Object> parameters = new ArrayList<>();
		appendFilters(where, parameters, search);

		Long total = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM products product" + where,
			Long.class,
			parameters.toArray());

		String sql = """
			SELECT
				BIN_TO_UUID(product.public_id) product_public_id,
				product.name,
				product.slug,
				product.status,
				product.version,
				product.updated_at,
				BIN_TO_UUID(category.public_id) category_public_id,
				category.name category_name,
				category.status category_status,
				BIN_TO_UUID(image.public_id) image_public_id,
				image.alt_text image_alt_text,
				(SELECT COUNT(*) FROM product_variants variant
					WHERE variant.product_id = product.id) variant_count,
				(SELECT COUNT(*) FROM product_variants variant
					WHERE variant.product_id = product.id
						AND variant.status = 'ACTIVE') active_variant_count,
				(SELECT MIN(variant.price) FROM product_variants variant
					WHERE variant.product_id = product.id) price_from,
				(SELECT MAX(variant.price) FROM product_variants variant
					WHERE variant.product_id = product.id) price_to
			FROM products product
			JOIN categories category ON category.id = product.category_id
			LEFT JOIN product_images image ON image.product_id = product.id
			""" + where + " ORDER BY product.updated_at DESC, product.id DESC LIMIT ? OFFSET ?";
		parameters.add(search.size());
		parameters.add(Math.multiplyExact((long) search.page(), search.size()));

		List<ProductSummary> items = jdbcTemplate.query(
			sql,
			(resultSet, rowNumber) -> new ProductSummary(
				UUID.fromString(resultSet.getString("product_public_id")),
				resultSet.getString("name"),
				resultSet.getString("slug"),
				ProductStatus.valueOf(resultSet.getString("status")),
				mapCategory(resultSet),
				mapImage(resultSet),
				resultSet.getLong("variant_count"),
				resultSet.getLong("active_variant_count"),
				resultSet.getBigDecimal("price_from"),
				resultSet.getBigDecimal("price_to"),
				resultSet.getLong("version"),
				resultSet.getTimestamp("updated_at").toInstant()),
			parameters.toArray());
		return new ProductPage(items, search.page(), search.size(), total == null ? 0 : total);
	}

	@Override
	public Optional<Product> findById(UUID productId) {
		List<ProductHeader> products = jdbcTemplate.query(
			PRODUCT_HEADER + " WHERE product.public_id = UUID_TO_BIN(?)",
			this::mapProductHeader,
			productId.toString());
		if (products.isEmpty()) {
			return Optional.empty();
		}
		ProductHeader header = products.getFirst();
		List<ProductVariant> variants = jdbcTemplate.query(
			VARIANT_COLUMNS + """
				 WHERE variant.product_id = (
					SELECT id FROM products WHERE public_id = UUID_TO_BIN(?)
				)
				ORDER BY variant.created_at, variant.id
				""",
			variantMapper,
			productId.toString());
		return Optional.of(header.toProduct(withOptions(variants)));
	}

	@Override
	public Optional<ProductVariant> findVariant(UUID productId, UUID variantId) {
		List<ProductVariant> variants = jdbcTemplate.query(
			VARIANT_COLUMNS + """
				JOIN products product ON product.id = variant.product_id
				WHERE product.public_id = UUID_TO_BIN(?)
					AND variant.public_id = UUID_TO_BIN(?)
				""",
			variantMapper,
			productId.toString(),
			variantId.toString());
		return withOptions(variants).stream().findFirst();
	}

	@Override
	public Optional<LockedProduct> lockProduct(UUID productId) {
		return jdbcTemplate.query("""
			SELECT id, category_id, status, version
			FROM products
			WHERE public_id = UUID_TO_BIN(?)
			FOR UPDATE
			""",
			(resultSet, rowNumber) -> new LockedProduct(
				resultSet.getLong("id"),
				resultSet.getLong("category_id"),
				ProductStatus.valueOf(resultSet.getString("status")),
				resultSet.getLong("version")),
			productId.toString())
			.stream()
			.findFirst();
	}

	@Override
	public Optional<LockedVariant> lockVariant(long productInternalId, UUID variantId) {
		return jdbcTemplate.query("""
			SELECT id, status, version
			FROM product_variants
			WHERE product_id = ?
				AND public_id = UUID_TO_BIN(?)
			FOR UPDATE
			""",
			(resultSet, rowNumber) -> new LockedVariant(
				resultSet.getLong("id"),
				"ACTIVE".equals(resultSet.getString("status")),
				resultSet.getLong("version")),
			productInternalId,
			variantId.toString())
			.stream()
			.findFirst();
	}

	@Override
	public Optional<Long> lockActiveCategory(UUID categoryId) {
		return jdbcTemplate.query("""
			SELECT id
			FROM categories
			WHERE public_id = UUID_TO_BIN(?)
				AND status = 'ACTIVE'
			FOR UPDATE
			""",
			(resultSet, rowNumber) -> resultSet.getLong("id"),
			categoryId.toString())
			.stream()
			.findFirst();
	}

	@Override
	public boolean lockCategoryIsActive(long categoryInternalId) {
		return jdbcTemplate.query("""
			SELECT status
			FROM categories
			WHERE id = ?
			FOR UPDATE
			""",
			(resultSet, rowNumber) -> "ACTIVE".equals(resultSet.getString("status")),
			categoryInternalId)
			.stream()
			.findFirst()
			.orElse(false);
	}

	@Override
	public long insertProduct(
			UUID publicId,
			long categoryInternalId,
			String name,
			String slug,
			String description) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		try {
			jdbcTemplate.update(connection -> {
				PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO products (
						public_id, category_id, name, slug, description, status
					)
					VALUES (UUID_TO_BIN(?), ?, ?, ?, ?, 'DRAFT')
					""", Statement.RETURN_GENERATED_KEYS);
				statement.setString(1, publicId.toString());
				statement.setLong(2, categoryInternalId);
				statement.setString(3, name);
				statement.setString(4, slug);
				statement.setString(5, description);
				return statement;
			}, keyHolder);
		}
		catch (DuplicateKeyException exception) {
			throw conflict();
		}
		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("MySQL did not return the product identifier");
		}
		return key.longValue();
	}

	@Override
	public void insertVariant(
			UUID publicId,
			long productInternalId,
			VariantValues values) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		try {
			jdbcTemplate.update(connection -> {
				PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO product_variants (
					public_id, product_id, sku, price, size_value, color_value,
					option_signature, status
				)
				VALUES (UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, 'ACTIVE')
				""", Statement.RETURN_GENERATED_KEYS);
				statement.setString(1, publicId.toString());
				statement.setLong(2, productInternalId);
				statement.setString(3, values.sku());
				statement.setBigDecimal(4, values.price());
				statement.setString(5, values.size());
				statement.setString(6, values.color());
				statement.setString(7, values.optionSignature());
				return statement;
			}, keyHolder);
			Number key = keyHolder.getKey();
			if (key == null) {
				throw new IllegalStateException("MySQL did not return the variant identifier");
			}
			replaceVariantOptions(key.longValue(), productInternalId, values.options());
		}
		catch (DuplicateKeyException exception) {
			throw conflict();
		}
	}

	@Override
	public boolean updateProduct(
			long internalId,
			long categoryInternalId,
			String name,
			String description,
			long expectedVersion) {
		try {
			return jdbcTemplate.update("""
				UPDATE products
				SET category_id = ?, name = ?, description = ?,
					version = version + 1
				WHERE id = ? AND version = ?
				""",
				categoryInternalId,
				name,
				description,
				internalId,
				expectedVersion) > 0;
		}
		catch (DuplicateKeyException exception) {
			throw conflict();
		}
	}

	@Override
	public boolean updateProductStatus(
			long internalId,
			ProductStatus status,
			long expectedVersion) {
		return jdbcTemplate.update("""
			UPDATE products
			SET status = ?, version = version + 1
			WHERE id = ? AND version = ?
			""",
			status.name(),
			internalId,
			expectedVersion) > 0;
	}

	@Override
	public boolean updateVariant(
			long internalId,
			long productInternalId,
			VariantValues values,
			long expectedVersion) {
		try {
			boolean updated = jdbcTemplate.update("""
				UPDATE product_variants
				SET sku = ?, price = ?, size_value = ?, color_value = ?, option_signature = ?,
					version = version + 1
				WHERE id = ? AND version = ?
				""",
				values.sku(),
				values.price(),
				values.size(),
				values.color(),
				values.optionSignature(),
				internalId,
				expectedVersion) > 0;
			if (updated) {
				replaceVariantOptions(internalId, productInternalId, values.options());
			}
			return updated;
		}
		catch (DuplicateKeyException exception) {
			throw conflict();
		}
	}

	@Override
	public boolean updateVariantStatus(
			long internalId,
			boolean active,
			long expectedVersion) {
		return jdbcTemplate.update("""
			UPDATE product_variants
			SET status = ?, version = version + 1
			WHERE id = ? AND version = ?
			""",
			active ? "ACTIVE" : "INACTIVE",
			internalId,
			expectedVersion) > 0;
	}

	@Override
	public int countActiveVariants(long productInternalId) {
		Integer count = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM product_variants
			WHERE product_id = ? AND status = 'ACTIVE'
			""",
			Integer.class,
			productInternalId);
		return count == null ? 0 : count;
	}

	private void appendFilters(
			StringBuilder where,
			List<Object> parameters,
			ProductSearch search) {
		if (search.status() != ProductStatusFilter.ALL) {
			where.append(" AND product.status = ?");
			parameters.add(search.status().name());
		}
		if (search.categoryId() != null) {
			where.append("""
				 AND product.category_id = (
					SELECT id FROM categories WHERE public_id = UUID_TO_BIN(?)
				)
				""");
			parameters.add(search.categoryId().toString());
		}
		if (search.query() != null) {
			where.append("""
				 AND (
					product.name LIKE ?
					OR EXISTS (
						SELECT 1 FROM product_variants searched_variant
						WHERE searched_variant.product_id = product.id
							AND searched_variant.sku LIKE ?
					)
				)
				""");
			String pattern = "%" + search.query() + "%";
			parameters.add(pattern);
			parameters.add(pattern);
		}
	}

	private ProductHeader mapProductHeader(ResultSet resultSet, int rowNumber)
			throws SQLException {
		return new ProductHeader(
			UUID.fromString(resultSet.getString("product_public_id")),
			resultSet.getString("name"),
			resultSet.getString("slug"),
			resultSet.getString("description"),
			ProductStatus.valueOf(resultSet.getString("status")),
			mapCategory(resultSet),
			mapImage(resultSet),
			resultSet.getLong("version"),
			resultSet.getTimestamp("created_at").toInstant(),
			resultSet.getTimestamp("updated_at").toInstant());
	}

	private ProductCategory mapCategory(ResultSet resultSet) throws SQLException {
		return new ProductCategory(
			UUID.fromString(resultSet.getString("category_public_id")),
			resultSet.getString("category_name"),
			"ACTIVE".equals(resultSet.getString("category_status")));
	}

	private ProductImageReference mapImage(ResultSet resultSet) throws SQLException {
		String id = resultSet.getString("image_public_id");
		return id == null ? null : new ProductImageReference(
			UUID.fromString(id), resultSet.getString("image_alt_text"));
	}

	private ProductVariant mapVariant(ResultSet resultSet, int rowNumber)
			throws SQLException {
		return new ProductVariant(
			UUID.fromString(resultSet.getString("variant_public_id")),
			resultSet.getString("sku"),
			resultSet.getBigDecimal("price"),
			nullableOption(resultSet.getString("size_value")),
			nullableOption(resultSet.getString("color_value")),
			List.of(),
			"ACTIVE".equals(resultSet.getString("status")),
			resultSet.getLong("version"),
			resultSet.getTimestamp("created_at").toInstant(),
			resultSet.getTimestamp("updated_at").toInstant());
	}

	private List<ProductVariant> withOptions(List<ProductVariant> variants) {
		if (variants.isEmpty()) {
			return variants;
		}
		String placeholders = String.join(", ",
			Collections.nCopies(variants.size(), "UUID_TO_BIN(?)"));
		Map<UUID, List<VariantOptionValue>> optionsByVariant = new HashMap<>();
		jdbcTemplate.query("""
			SELECT BIN_TO_UUID(variant.public_id) variant_public_id,
				product_option.name, option_value.value
			FROM product_variants variant
			JOIN product_variant_option_values relation ON relation.variant_id = variant.id
			JOIN product_option_values option_value ON option_value.id = relation.option_value_id
			JOIN product_options product_option ON product_option.id = option_value.option_id
			WHERE variant.public_id IN (
			""" + placeholders + ") ORDER BY product_option.position, option_value.position",
			(org.springframework.jdbc.core.RowCallbackHandler) resultSet -> optionsByVariant
				.computeIfAbsent(
					UUID.fromString(resultSet.getString("variant_public_id")),
					ignored -> new ArrayList<>())
				.add(new VariantOptionValue(
					resultSet.getString("name"), resultSet.getString("value"))),
			variants.stream().map(variant -> variant.id().toString()).toArray());
		return variants.stream().map(variant -> new ProductVariant(
			variant.id(),
			variant.sku(),
			variant.price(),
			variant.size(),
			variant.color(),
			List.copyOf(optionsByVariant.getOrDefault(variant.id(), List.of())),
			variant.active(),
			variant.version(),
			variant.createdAt(),
			variant.updatedAt())).toList();
	}

	private void replaceVariantOptions(
			long variantInternalId,
			long productInternalId,
			List<VariantOptionValue> options) {
		jdbcTemplate.update(
			"DELETE FROM product_variant_option_values WHERE variant_id = ?",
			variantInternalId);
		jdbcTemplate.update("""
			DELETE option_value
			FROM product_option_values option_value
			JOIN product_options product_option ON product_option.id = option_value.option_id
			LEFT JOIN product_variant_option_values relation
				ON relation.option_value_id = option_value.id
			WHERE product_option.product_id = ? AND relation.variant_id IS NULL
			""", productInternalId);
		jdbcTemplate.update("""
			DELETE product_option
			FROM product_options product_option
			LEFT JOIN product_option_values option_value
				ON option_value.option_id = product_option.id
			WHERE product_option.product_id = ? AND option_value.id IS NULL
			""", productInternalId);
		for (VariantOptionValue option : options) {
			long optionId = findOrCreateOption(productInternalId, option.name());
			long valueId = findOrCreateOptionValue(optionId, option.value());
			jdbcTemplate.update("""
				INSERT INTO product_variant_option_values (variant_id, option_value_id)
				VALUES (?, ?)
				""", variantInternalId, valueId);
		}
	}

	private long findOrCreateOption(long productInternalId, String name) {
		String normalized = name.toLowerCase(Locale.ROOT);
		List<Long> existing = jdbcTemplate.query("""
			SELECT id FROM product_options
			WHERE product_id = ? AND normalized_name = ?
			""", (resultSet, rowNumber) -> resultSet.getLong("id"),
			productInternalId, normalized);
		if (!existing.isEmpty()) {
			return existing.getFirst();
		}
		int position = nextOptionPosition(productInternalId);
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO product_options (
					public_id, product_id, name, normalized_name, position
				) VALUES (UUID_TO_BIN(?), ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, UUID.randomUUID().toString());
			statement.setLong(2, productInternalId);
			statement.setString(3, name);
			statement.setString(4, normalized);
			statement.setInt(5, position);
			return statement;
		}, keyHolder);
		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("MySQL did not return the option identifier");
		}
		return key.longValue();
	}

	private long findOrCreateOptionValue(long optionId, String value) {
		String normalized = value.toLowerCase(Locale.ROOT);
		List<Long> existing = jdbcTemplate.query("""
			SELECT id FROM product_option_values
			WHERE option_id = ? AND normalized_value = ?
			""", (resultSet, rowNumber) -> resultSet.getLong("id"), optionId, normalized);
		if (!existing.isEmpty()) {
			return existing.getFirst();
		}
		int position = nextOptionValuePosition(optionId);
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO product_option_values (
					public_id, option_id, value, normalized_value, position
				) VALUES (UUID_TO_BIN(?), ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, UUID.randomUUID().toString());
			statement.setLong(2, optionId);
			statement.setString(3, value);
			statement.setString(4, normalized);
			statement.setInt(5, position);
			return statement;
		}, keyHolder);
		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("MySQL did not return the option value identifier");
		}
		return key.longValue();
	}

	private int nextOptionPosition(long productInternalId) {
		List<Integer> used = jdbcTemplate.queryForList(
			"SELECT position FROM product_options WHERE product_id = ?",
			Integer.class,
			productInternalId);
		for (int position = 1; position <= 5; position++) {
			if (!used.contains(position)) return position;
		}
		throw new ProductConflictException(
			"El producto admite hasta 5 nombres de opción diferentes.");
	}

	private int nextOptionValuePosition(long optionId) {
		List<Integer> used = jdbcTemplate.queryForList(
			"SELECT position FROM product_option_values WHERE option_id = ?",
			Integer.class,
			optionId);
		for (int position = 1; position <= 100; position++) {
			if (!used.contains(position)) return position;
		}
		throw new ProductConflictException(
			"Una opción del producto admite hasta 100 valores diferentes.");
	}

	private String nullableOption(String value) {
		return value == null || value.isEmpty() ? null : value;
	}

	private ProductConflictException conflict() {
		return new ProductConflictException(
			"El slug, SKU o combinación de opciones ya existe.");
	}

	private record ProductHeader(
		UUID id,
		String name,
		String slug,
		String description,
		ProductStatus status,
		ProductCategory category,
		ProductImageReference image,
		long version,
		java.time.Instant createdAt,
		java.time.Instant updatedAt) {

		Product toProduct(List<ProductVariant> variants) {
			return new Product(
				id,
				name,
				slug,
				description,
				status,
				category,
				image,
				variants,
				version,
				createdAt,
				updatedAt);
		}
	}
}
