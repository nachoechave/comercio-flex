package com.comercioflex.media.infrastructure.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.comercioflex.media.application.ProductImageRepository;
import com.comercioflex.media.application.LockedImageProduct;
import com.comercioflex.media.domain.ProductImage;

@Repository
public class JdbcProductImageRepository implements ProductImageRepository {

	private static final String SELECT = """
		SELECT BIN_TO_UUID(image.public_id) image_public_id,
			BIN_TO_UUID(product.public_id) product_public_id,
			image.display_storage_key, image.thumbnail_storage_key,
			image.content_type, image.display_byte_size, image.thumbnail_byte_size,
			image.width, image.height, image.alt_text, image.sha256,
			image.version, image.updated_at
		FROM product_images image
		JOIN products product ON product.id = image.product_id
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcProductImageRepository(@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<LockedImageProduct> lockProduct(UUID productId) {
		return jdbcTemplate.query("""
			SELECT id, status FROM products WHERE public_id = UUID_TO_BIN(?) FOR UPDATE
			""", (rs, row) -> new LockedImageProduct(
				rs.getLong("id"), "ARCHIVED".equals(rs.getString("status"))),
			productId.toString()).stream().findFirst();
	}

	@Override
	public Optional<ProductImage> findByProductId(UUID productId) {
		return jdbcTemplate.query(SELECT + " WHERE product.public_id = UUID_TO_BIN(?)",
			this::map, productId.toString()).stream().findFirst();
	}

	@Override
	public Optional<ProductImage> findByPublicId(UUID imageId, boolean requirePublishedProduct) {
		String publication = requirePublishedProduct ? " AND product.status = 'PUBLISHED'" : "";
		return jdbcTemplate.query(SELECT
			+ " WHERE image.public_id = UUID_TO_BIN(?)" + publication,
			this::map, imageId.toString()).stream().findFirst();
	}

	@Override
	public Optional<ProductImage> upsert(long productInternalId, ProductImage image) {
		Optional<ProductImage> previous = jdbcTemplate.query(SELECT
			+ " WHERE image.product_id = ? FOR UPDATE", this::map, productInternalId)
			.stream().findFirst();
		jdbcTemplate.update("""
			INSERT INTO product_images (
				public_id, product_id, display_storage_key, thumbnail_storage_key,
				content_type, display_byte_size, thumbnail_byte_size, width, height,
				alt_text, sha256
			) VALUES (UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON DUPLICATE KEY UPDATE
				public_id = VALUES(public_id),
				display_storage_key = VALUES(display_storage_key),
				thumbnail_storage_key = VALUES(thumbnail_storage_key),
				content_type = VALUES(content_type),
				display_byte_size = VALUES(display_byte_size),
				thumbnail_byte_size = VALUES(thumbnail_byte_size),
				width = VALUES(width), height = VALUES(height),
				alt_text = VALUES(alt_text), sha256 = VALUES(sha256),
				version = version + 1
			""",
			image.id().toString(), productInternalId,
			image.displayStorageKey(), image.thumbnailStorageKey(), image.contentType(),
			image.displayByteSize(), image.thumbnailByteSize(), image.width(), image.height(),
			image.altText(), image.sha256());
		return previous;
	}

	@Override
	public Optional<ProductImage> delete(UUID productId) {
		Optional<ProductImage> existing = jdbcTemplate.query(SELECT
			+ " WHERE product.public_id = UUID_TO_BIN(?) FOR UPDATE",
			this::map, productId.toString()).stream().findFirst();
		existing.ifPresent(image -> jdbcTemplate.update(
			"DELETE FROM product_images WHERE public_id = UUID_TO_BIN(?)",
			image.id().toString()));
		return existing;
	}

	private ProductImage map(ResultSet rs, int row) throws SQLException {
		return new ProductImage(
			UUID.fromString(rs.getString("image_public_id")),
			UUID.fromString(rs.getString("product_public_id")),
			rs.getString("display_storage_key"),
			rs.getString("thumbnail_storage_key"),
			rs.getString("content_type"),
			rs.getLong("display_byte_size"),
			rs.getLong("thumbnail_byte_size"),
			rs.getInt("width"), rs.getInt("height"), rs.getString("alt_text"),
			rs.getString("sha256"), rs.getLong("version"),
			rs.getTimestamp("updated_at").toInstant());
	}
}
