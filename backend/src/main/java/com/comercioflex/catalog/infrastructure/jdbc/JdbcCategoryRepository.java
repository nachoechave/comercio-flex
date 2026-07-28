package com.comercioflex.catalog.infrastructure.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.comercioflex.catalog.application.CategoryConflictException;
import com.comercioflex.catalog.application.CategoryRepository;
import com.comercioflex.catalog.application.CategoryStatusFilter;
import com.comercioflex.catalog.domain.Category;
import com.comercioflex.catalog.domain.CategoryStatus;

@Repository
public class JdbcCategoryRepository implements CategoryRepository {

	private static final String CATEGORY_COLUMNS = """
		SELECT
			BIN_TO_UUID(public_id) AS public_id,
			name,
			slug,
			status,
			created_at,
			updated_at
		FROM categories
		""";

	private final JdbcTemplate jdbcTemplate;
	private final RowMapper<Category> rowMapper = this::mapCategory;

	public JdbcCategoryRepository(
			@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<Category> findAll(CategoryStatusFilter status) {
		if (status == CategoryStatusFilter.ALL) {
			return jdbcTemplate.query(CATEGORY_COLUMNS + " ORDER BY name, id", rowMapper);
		}
		return jdbcTemplate.query(
			CATEGORY_COLUMNS + " WHERE status = ? ORDER BY name, id",
			rowMapper,
			status.name());
	}

	@Override
	public Optional<Category> findById(UUID id) {
		return jdbcTemplate.query(
			CATEGORY_COLUMNS + " WHERE public_id = UUID_TO_BIN(?)",
			rowMapper,
			id.toString())
			.stream()
			.findFirst();
	}

	@Override
	public void insert(UUID id, String name, String slug, CategoryStatus status) {
		try {
			jdbcTemplate.update("""
				INSERT INTO categories (public_id, name, slug, status)
				VALUES (UUID_TO_BIN(?), ?, ?, ?)
				""",
				id.toString(),
				name,
				slug,
				status.name());
		}
		catch (DuplicateKeyException exception) {
			throw new CategoryConflictException();
		}
	}

	@Override
	public boolean rename(UUID id, String name) {
		try {
			return jdbcTemplate.update("""
				UPDATE categories
				SET name = ?
				WHERE public_id = UUID_TO_BIN(?)
				""",
				name,
				id.toString()) > 0;
		}
		catch (DuplicateKeyException exception) {
			throw new CategoryConflictException();
		}
	}

	@Override
	public boolean changeStatus(UUID id, CategoryStatus status) {
		return jdbcTemplate.update("""
			UPDATE categories
			SET status = ?
			WHERE public_id = UUID_TO_BIN(?)
			""",
			status.name(),
			id.toString()) > 0;
	}

	private Category mapCategory(ResultSet resultSet, int rowNumber) throws SQLException {
		return new Category(
			UUID.fromString(resultSet.getString("public_id")),
			resultSet.getString("name"),
			resultSet.getString("slug"),
			CategoryStatus.valueOf(resultSet.getString("status")),
			resultSet.getTimestamp("created_at").toInstant(),
			resultSet.getTimestamp("updated_at").toInstant());
	}
}
