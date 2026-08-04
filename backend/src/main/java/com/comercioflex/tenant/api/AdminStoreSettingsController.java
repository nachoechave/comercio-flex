package com.comercioflex.tenant.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.identity.application.TenantPermissionGuard;
import com.comercioflex.identity.domain.TenantPermission;
import com.comercioflex.tenant.application.StoreSettingsQueryService;
import com.comercioflex.tenant.application.StoreSettingsService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/admin/settings")
public class AdminStoreSettingsController {

	private final StoreSettingsQueryService queryService;
	private final StoreSettingsService service;
	private final TenantPermissionGuard permissionGuard;

	public AdminStoreSettingsController(StoreSettingsQueryService queryService,
			StoreSettingsService service, TenantPermissionGuard permissionGuard) {
		this.queryService = queryService;
		this.service = service;
		this.permissionGuard = permissionGuard;
	}

	@GetMapping
	StoreSettingsResponse find(@PathVariable String storeSlug, HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.MANAGE_BASIC_SETTINGS);
		return StoreSettingsResponse.from(storeSlug, queryService.findCurrent());
	}

	@PutMapping
	StoreSettingsResponse update(@PathVariable String storeSlug,
			@Valid @RequestBody UpdateStoreSettingsRequest body, HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.MANAGE_BASIC_SETTINGS);
		return StoreSettingsResponse.from(storeSlug, service.update(body.toCommand()));
	}
}
