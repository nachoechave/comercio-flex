package com.comercioflex.tenant.infrastructure.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.comercioflex.tenant.application.TenantBrandingRepository;
import com.comercioflex.tenant.application.UpdateTenantBrandingCommand;
import com.comercioflex.tenant.domain.BrandAssetReference;
import com.comercioflex.tenant.domain.BrandAssetType;
import com.comercioflex.tenant.domain.BrandFont;
import com.comercioflex.tenant.domain.StorefrontTemplate;
import com.comercioflex.tenant.domain.TenantBranding;

@Repository
public class JdbcTenantBrandingRepository implements TenantBrandingRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcTenantBrandingRepository(
			@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<TenantBranding> findCurrent() {
		return jdbcTemplate.query("""
			SELECT primary_color, secondary_color, background_color, text_color,
			       brand_font, hero_title, hero_subtitle, storefront_template,
			       logo_storage_key, logo_content_type, logo_etag,
			       favicon_storage_key, favicon_content_type, favicon_etag,
			       hero_storage_key, hero_content_type, hero_etag
			FROM store_settings
			ORDER BY id
			LIMIT 1
			""", (resultSet, rowNumber) -> map(resultSet)).stream().findFirst();
	}

	@Override
	public void update(UpdateTenantBrandingCommand command) {
		jdbcTemplate.update("""
			UPDATE store_settings
			SET primary_color = ?, secondary_color = ?, background_color = ?,
			    text_color = ?, brand_font = ?, hero_title = ?, hero_subtitle = ?,
			    storefront_template = ?
			ORDER BY id
			LIMIT 1
			""", command.primaryColor(), command.secondaryColor(), command.backgroundColor(),
			command.textColor(), command.font().name(), command.heroTitle(),
			command.heroSubtitle(), command.template().name());
	}

	@Override
	public BrandAssetReference replaceAsset(
			BrandAssetType type,
			BrandAssetReference reference) {
		BrandAssetReference previous = currentAsset(type);
		String prefix = prefix(type);
		jdbcTemplate.update("UPDATE store_settings SET " + prefix + "_storage_key = ?, "
			+ prefix + "_content_type = ?, " + prefix + "_etag = ? ORDER BY id LIMIT 1",
			reference.storageKey(), reference.contentType(), reference.etag());
		return previous;
	}

	@Override
	public BrandAssetReference clearAsset(BrandAssetType type) {
		BrandAssetReference previous = currentAsset(type);
		String prefix = prefix(type);
		jdbcTemplate.update("UPDATE store_settings SET " + prefix + "_storage_key = NULL, "
			+ prefix + "_content_type = NULL, " + prefix + "_etag = NULL ORDER BY id LIMIT 1");
		return previous;
	}

	private BrandAssetReference currentAsset(BrandAssetType type) {
		return findCurrent().map(branding -> switch (type) {
			case LOGO -> branding.logo();
			case FAVICON -> branding.favicon();
			case HERO -> branding.hero();
		}).orElse(null);
	}

	private TenantBranding map(ResultSet resultSet) throws SQLException {
		return new TenantBranding(
			resultSet.getString("primary_color"),
			resultSet.getString("secondary_color"),
			resultSet.getString("background_color"),
			resultSet.getString("text_color"),
			BrandFont.valueOf(resultSet.getString("brand_font")),
			resultSet.getString("hero_title"),
			resultSet.getString("hero_subtitle"),
			StorefrontTemplate.valueOf(resultSet.getString("storefront_template")),
			asset(resultSet, "logo"), asset(resultSet, "favicon"), asset(resultSet, "hero"));
	}

	private BrandAssetReference asset(ResultSet resultSet, String prefix) throws SQLException {
		String key = resultSet.getString(prefix + "_storage_key");
		return key == null ? null : new BrandAssetReference(
			key, resultSet.getString(prefix + "_content_type"), resultSet.getString(prefix + "_etag"));
	}

	private String prefix(BrandAssetType type) {
		return type.name().toLowerCase(java.util.Locale.ROOT);
	}
}
