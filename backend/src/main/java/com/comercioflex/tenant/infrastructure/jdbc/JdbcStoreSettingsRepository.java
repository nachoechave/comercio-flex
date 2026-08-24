package com.comercioflex.tenant.infrastructure.jdbc;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.comercioflex.tenant.application.StoreSettingsRepository;
import com.comercioflex.tenant.application.UpdateStoreSettingsCommand;
import com.comercioflex.tenant.domain.BrandTheme;
import com.comercioflex.tenant.domain.BrandAssetReference;
import com.comercioflex.tenant.domain.BrandFont;
import com.comercioflex.tenant.domain.StoreSettings;
import com.comercioflex.tenant.domain.StorefrontTemplate;
import com.comercioflex.tenant.domain.TenantBranding;

@Repository
public class JdbcStoreSettingsRepository implements StoreSettingsRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcStoreSettingsRepository(@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<StoreSettings> findCurrent() {
		List<StoreSettings> result = jdbcTemplate.query("""
				SELECT store_name, currency_code, timezone, contact_phone, contact_email,
				       pickup_address, pickup_instructions, bank_transfer_enabled,
				       bank_name, bank_account_holder, bank_alias, bank_cbu_cvu, brand_theme,
				       primary_color, secondary_color, background_color, text_color,
				       brand_font, hero_title, hero_subtitle, storefront_template,
				       logo_storage_key, logo_content_type, logo_etag,
				       favicon_storage_key, favicon_content_type, favicon_etag,
				       hero_storage_key, hero_content_type, hero_etag
				FROM store_settings
				ORDER BY id
				LIMIT 1
				""", (resultSet, rowNumber) -> new StoreSettings(
			resultSet.getString("store_name"),
			resultSet.getString("currency_code"),
			resultSet.getString("timezone"),
			resultSet.getString("contact_phone"),
			resultSet.getString("contact_email"),
			resultSet.getString("pickup_address"),
			resultSet.getString("pickup_instructions"),
			resultSet.getBoolean("bank_transfer_enabled"),
			resultSet.getString("bank_name"),
			resultSet.getString("bank_account_holder"),
			resultSet.getString("bank_alias"),
			resultSet.getString("bank_cbu_cvu"),
			BrandTheme.valueOf(resultSet.getString("brand_theme")),
			new TenantBranding(
				resultSet.getString("primary_color"),
				resultSet.getString("secondary_color"),
				resultSet.getString("background_color"),
				resultSet.getString("text_color"),
				BrandFont.valueOf(resultSet.getString("brand_font")),
				resultSet.getString("hero_title"),
				resultSet.getString("hero_subtitle"),
				StorefrontTemplate.valueOf(resultSet.getString("storefront_template")),
				asset(resultSet, "logo"),
				asset(resultSet, "favicon"),
				asset(resultSet, "hero"))));
		return result.stream().findFirst();
	}

	@Override
	public void update(UpdateStoreSettingsCommand command) {
		jdbcTemplate.update("""
				UPDATE store_settings
				SET store_name = ?, contact_phone = ?, contact_email = ?, pickup_address = ?,
				    pickup_instructions = ?, bank_transfer_enabled = ?, bank_name = ?,
				    bank_account_holder = ?, bank_alias = ?, bank_cbu_cvu = ?
				ORDER BY id
				LIMIT 1
				""",
			command.storeName(), command.contactPhone(), command.contactEmail(),
			command.pickupAddress(), command.pickupInstructions(),
			command.bankTransferEnabled(), command.bankName(), command.bankAccountHolder(),
			command.bankAlias(), command.bankCbuCvu());
	}

	private BrandAssetReference asset(java.sql.ResultSet resultSet, String prefix)
			throws java.sql.SQLException {
		String key = resultSet.getString(prefix + "_storage_key");
		return key == null ? null : new BrandAssetReference(
			key,
			resultSet.getString(prefix + "_content_type"),
			resultSet.getString(prefix + "_etag"));
	}
}
