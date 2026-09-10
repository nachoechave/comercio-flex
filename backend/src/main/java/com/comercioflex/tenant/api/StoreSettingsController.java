package com.comercioflex.tenant.api;

import org.springframework.web.bind.annotation.RequestAttribute;

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

	public StoreSettingsController(StoreSettingsQueryService storeSettingsQueryService) {
		this.storeSettingsQueryService = storeSettingsQueryService;
	}

	@GetMapping
	StoreSettingsResponse getSettings(@PathVariable String slug,
			@RequestAttribute(TenantResolutionFilter.RESOLVED_TENANT_ATTRIBUTE)
			ResolvedTenant tenant) {
		return StoreSettingsResponse.from(slug, storeSettingsQueryService.findCurrent(), tenant.tenantType());
	}
}
