package com.comercioflex.catalog.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.comercioflex.catalog.application.CategoryService;
import com.comercioflex.catalog.application.CategoryStatusFilter;
import com.comercioflex.identity.application.TenantPermissionGuard;
import com.comercioflex.identity.domain.TenantPermission;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/admin/categories")
public class AdminCategoryController {

	private final CategoryService categoryService;
	private final TenantPermissionGuard permissionGuard;

	public AdminCategoryController(
			CategoryService categoryService,
			TenantPermissionGuard permissionGuard) {
		this.categoryService = categoryService;
		this.permissionGuard = permissionGuard;
	}

	@GetMapping
	List<CategoryResponse> findAll(
			@RequestParam(defaultValue = "ALL") CategoryStatusFilter status,
			HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.VIEW_CATALOG);
		return categoryService.findAll(status).stream()
			.map(CategoryResponse::from)
			.toList();
	}

	@PostMapping
	ResponseEntity<CategoryResponse> create(
			@Valid @RequestBody CreateCategoryRequest body,
			HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.MANAGE_CATALOG);
		CategoryResponse response = CategoryResponse.from(categoryService.create(body.name()));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
			.path("/{id}")
			.buildAndExpand(response.id())
			.toUri();
		return ResponseEntity.created(location).body(response);
	}

	@GetMapping("/{categoryId}")
	CategoryResponse findById(
			@PathVariable UUID categoryId,
			HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.VIEW_CATALOG);
		return CategoryResponse.from(categoryService.findById(categoryId));
	}

	@PutMapping("/{categoryId}")
	CategoryResponse rename(
			@PathVariable UUID categoryId,
			@Valid @RequestBody RenameCategoryRequest body,
			HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.MANAGE_CATALOG);
		return CategoryResponse.from(categoryService.rename(categoryId, body.name()));
	}

	@PatchMapping("/{categoryId}/status")
	CategoryResponse changeStatus(
			@PathVariable UUID categoryId,
			@Valid @RequestBody ChangeCategoryStatusRequest body,
			HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.MANAGE_CATALOG);
		return CategoryResponse.from(
			categoryService.changeStatus(categoryId, body.active()));
	}
}
