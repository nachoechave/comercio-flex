package com.comercioflex.tenant.infrastructure.jdbc;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.comercioflex.tenant.application.StoreSettingsRepository;
import com.comercioflex.tenant.application.UpdateStoreSettingsCommand;
import com.comercioflex.tenant.domain.BrandTheme;
import com.comercioflex.tenant.domain.StoreSettings;

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
				       pickup_address, pickup_instructions, brand_theme
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
			BrandTheme.valueOf(resultSet.getString("brand_theme"))));
		return result.stream().findFirst();
	}

	@Override
	public void update(UpdateStoreSettingsCommand command) {
		jdbcTemplate.update("""
				UPDATE store_settings
				SET store_name = ?, contact_phone = ?, contact_email = ?, pickup_address = ?,
				    pickup_instructions = ?, brand_theme = ?
				ORDER BY id
				LIMIT 1
				""",
			command.storeName(), command.contactPhone(), command.contactEmail(),
			command.pickupAddress(), command.pickupInstructions(), command.brandTheme().name());
	}
}
