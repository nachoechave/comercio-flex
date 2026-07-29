package com.comercioflex.catalog.infrastructure.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.comercioflex.catalog.application.PublicCatalogRepository;
import com.comercioflex.catalog.application.PublicCatalogSearch;
import com.comercioflex.catalog.application.PublicProductPage;
import com.comercioflex.catalog.domain.PublicCategory;
import com.comercioflex.catalog.domain.PublicProductDetail;
import com.comercioflex.catalog.domain.PublicProductSummary;
import com.comercioflex.catalog.domain.PublicVariant;

@Repository
public class JdbcPublicCatalogRepository implements PublicCatalogRepository {

	private static final String VISIBLE_PRODUCT = """
		product.status = 'PUBLISHED'
		AND category.status = 'ACTIVE'
		AND EXISTS (
			SELECT 1
			FROM product_variants visible_variant
			WHERE visible_variant.product_id = product.id
				AND visible_variant.status = 'ACTIVE'
		)
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcPublicCatalogRepository(
			@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<PublicCategory> findVisibleCategories() {
		return jdbcTemplate.query("""
			SELECT
				BIN_TO_UUID(category.public_id) category_public_id,
				category.name category_name,
				category.slug category_slug
			FROM categories category
			WHERE category.status = 'ACTIVE'
				AND EXISTS (
					SELECT 1
					FROM products product
					WHERE product.category_id = category.id
						AND product.status = 'PUBLISHED'
						AND EXISTS (
							SELECT 1
							FROM product_variants variant
							WHERE variant.product_id = product.id
								AND variant.status = 'ACTIVE'
						)
				)
			ORDER BY category.name, category.id
			""",
			(resultSet, rowNumber) -> mapCategory(resultSet));
	}

	@Override
	public PublicProductPage findProducts(PublicCatalogSearch search) {
		StringBuilder where = new StringBuilder(" WHERE " + VISIBLE_PRODUCT);
		List<Object> parameters = new ArrayList<>();
		appendFilters(where, parameters, search);

		Long total = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM products product
			JOIN categories category ON category.id = product.category_id
			""" + where,
			Long.class,
			parameters.toArray());

		long totalItems = total == null ? 0 : total;
		long offset = Math.multiplyExact((long) search.page(), search.size());
		if (offset >= totalItems) {
			return new PublicProductPage(
				List.of(),
				search.page(),
				search.size(),
				totalItems);
		}

		parameters.add(search.size());
		parameters.add(offset);
		List<PublicProductSummary> products = jdbcTemplate.query("""
			SELECT
				BIN_TO_UUID(product.public_id) product_public_id,
				product.name product_name,
				product.slug product_slug,
				BIN_TO_UUID(category.public_id) category_public_id,
				category.name category_name,
				category.slug category_slug,
				(
					SELECT MIN(price)
					FROM product_variants priced_variant
					WHERE priced_variant.product_id = product.id
						AND priced_variant.status = 'ACTIVE'
				) price_from,
				(
					SELECT MAX(price)
					FROM product_variants priced_variant
					WHERE priced_variant.product_id = product.id
						AND priced_variant.status = 'ACTIVE'
				) price_to,
				EXISTS (
					SELECT 1
					FROM product_variants available_variant
					LEFT JOIN inventory_balances balance
						ON balance.variant_id = available_variant.id
					WHERE available_variant.product_id = product.id
						AND available_variant.status = 'ACTIVE'
						AND COALESCE(balance.quantity, 0.000) > 0
				) available
			FROM products product
			JOIN categories category ON category.id = product.category_id
			""" + where + " ORDER BY product.name, product.id LIMIT ? OFFSET ?",
			(resultSet, rowNumber) -> new PublicProductSummary(
				UUID.fromString(resultSet.getString("product_public_id")),
				resultSet.getString("product_name"),
				resultSet.getString("product_slug"),
				mapCategory(resultSet),
				resultSet.getBigDecimal("price_from"),
				resultSet.getBigDecimal("price_to"),
				resultSet.getBoolean("available")),
			parameters.toArray());
		return new PublicProductPage(
			products,
			search.page(),
			search.size(),
			totalItems);
	}

	@Override
	public Optional<PublicProductDetail> findProductBySlug(String productSlug) {
		List<ProductHeader> products = jdbcTemplate.query("""
			SELECT
				product.id product_internal_id,
				BIN_TO_UUID(product.public_id) product_public_id,
				product.name product_name,
				product.slug product_slug,
				product.description,
				BIN_TO_UUID(category.public_id) category_public_id,
				category.name category_name,
				category.slug category_slug
			FROM products product
			JOIN categories category ON category.id = product.category_id
			WHERE product.slug = ?
				AND """ + " " + VISIBLE_PRODUCT,
			(resultSet, rowNumber) -> new ProductHeader(
				resultSet.getLong("product_internal_id"),
				UUID.fromString(resultSet.getString("product_public_id")),
				resultSet.getString("product_name"),
				resultSet.getString("product_slug"),
				resultSet.getString("description"),
				mapCategory(resultSet)),
			productSlug);
		if (products.isEmpty()) {
			return Optional.empty();
		}
		ProductHeader product = products.getFirst();
		List<PublicVariant> variants = jdbcTemplate.query("""
			SELECT
				BIN_TO_UUID(variant.public_id) variant_public_id,
				variant.price,
				variant.size_value,
				variant.color_value,
				COALESCE(balance.quantity, 0.000) > 0 available
			FROM product_variants variant
			LEFT JOIN inventory_balances balance ON balance.variant_id = variant.id
			WHERE variant.product_id = ?
				AND variant.status = 'ACTIVE'
			ORDER BY variant.size_value, variant.color_value, variant.id
			""",
			(resultSet, rowNumber) -> new PublicVariant(
				UUID.fromString(resultSet.getString("variant_public_id")),
				resultSet.getBigDecimal("price"),
				nullableOption(resultSet.getString("size_value")),
				nullableOption(resultSet.getString("color_value")),
				resultSet.getBoolean("available")),
			product.internalId());
		return Optional.of(product.toDetail(variants));
	}

	private void appendFilters(
			StringBuilder where,
			List<Object> parameters,
			PublicCatalogSearch search) {
		if (search.categorySlug() != null) {
			where.append(" AND category.slug = ?");
			parameters.add(search.categorySlug());
		}
		if (search.query() != null) {
			where.append(" AND product.name LIKE ? ESCAPE '!'");
			String pattern = "%" + escapeLike(search.query()) + "%";
			parameters.add(pattern);
		}
	}

	private String escapeLike(String value) {
		return value
			.replace("!", "!!")
			.replace("%", "!%")
			.replace("_", "!_");
	}

	private PublicCategory mapCategory(ResultSet resultSet) throws SQLException {
		return new PublicCategory(
			UUID.fromString(resultSet.getString("category_public_id")),
			resultSet.getString("category_name"),
			resultSet.getString("category_slug"));
	}

	private String nullableOption(String value) {
		return value == null || value.isEmpty() ? null : value;
	}

	private record ProductHeader(
		long internalId,
		UUID id,
		String name,
		String slug,
		String description,
		PublicCategory category) {

		PublicProductDetail toDetail(List<PublicVariant> variants) {
			return new PublicProductDetail(
				id,
				name,
				slug,
				description,
				category,
				variants);
		}
	}
}
