package com.comercioflex.tenant.application;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.tenant.api.StoreSettingsResponse;

@Service
public class StoreSettingsQueryService {

	private final JdbcTemplate tenantJdbcTemplate;
	private final TransactionTemplate tenantTransactionTemplate;

	public StoreSettingsQueryService(
			@Qualifier("tenantJdbcTemplate") JdbcTemplate tenantJdbcTemplate,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate tenantTransactionTemplate) {
		this.tenantJdbcTemplate = tenantJdbcTemplate;
		this.tenantTransactionTemplate = tenantTransactionTemplate;
	}

	public StoreSettingsResponse findCurrent(String slug) {
		return tenantTransactionTemplate.execute(status -> {
			List<StoreSettingsResponse> settings = tenantJdbcTemplate.query("""
					SELECT store_name, currency_code, timezone
					FROM store_settings
					ORDER BY id
					LIMIT 1
					""",
				(resultSet, rowNumber) -> new StoreSettingsResponse(
					slug,
					resultSet.getString("store_name"),
					resultSet.getString("currency_code"),
					resultSet.getString("timezone")));

			if (settings.isEmpty()) {
				throw new TenantNotFoundException();
			}
			return settings.getFirst();
		});
	}
}
