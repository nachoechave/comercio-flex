package com.comercioflex.dashboard.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.dashboard.application.DashboardService;
import com.comercioflex.identity.application.TenantPermissionGuard;
import com.comercioflex.identity.domain.TenantPermission;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/admin/dashboard")
public class AdminDashboardController {

	private final DashboardService service;
	private final TenantPermissionGuard permissionGuard;

	public AdminDashboardController(
			DashboardService service,
			TenantPermissionGuard permissionGuard) {
		this.service = service;
		this.permissionGuard = permissionGuard;
	}

	@GetMapping
	DashboardSummaryResponse find(HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.VIEW_DASHBOARD);
		return DashboardSummaryResponse.from(service.findSummary());
	}

	@PutMapping("/settings")
	DashboardSummaryResponse updateSettings(
			@Valid @RequestBody UpdateDashboardSettingsRequest body,
			HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.MANAGE_BASIC_SETTINGS);
		return DashboardSummaryResponse.from(
			service.updateLowStockThreshold(body.lowStockThreshold()));
	}
}
