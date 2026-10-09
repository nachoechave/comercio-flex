package com.comercioflex.tenant.api;

import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;

import com.comercioflex.tenant.application.ResolvedTenant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.tenant.application.StoreSettingsQueryService;

@RestController
@RequestMapping("/api/v1/stores/{slug}/settings")
public class StoreSettingsController {

	private final StoreSettingsQueryService storeSettingsQueryService;
	private final JdbcTemplate controlJdbcTemplate;

	public StoreSettingsController(StoreSettingsQueryService storeSettingsQueryService,
			@Qualifier("controlJdbcTemplate") JdbcTemplate controlJdbcTemplate) {
		this.storeSettingsQueryService = storeSettingsQueryService;
		this.controlJdbcTemplate = controlJdbcTemplate;
	}

	@GetMapping
	StoreSettingsResponse getSettings(@PathVariable String slug,
			@RequestAttribute(TenantResolutionFilter.RESOLVED_TENANT_ATTRIBUTE)
			ResolvedTenant tenant) {
		return StoreSettingsResponse.from(slug, storeSettingsQueryService.findCurrent(), tenant.tenantType());
	}
	@GetMapping("/industry")
	Map<String, String> getIndustry(
			@RequestAttribute(TenantResolutionFilter.RESOLVED_TENANT_ATTRIBUTE) ResolvedTenant tenant) {
		String industry = controlJdbcTemplate.queryForObject(
			"SELECT industry FROM tenants WHERE id = ?", String.class, tenant.id());
		return Map.of("industry", industry == null ? "" : industry);
	}
}

