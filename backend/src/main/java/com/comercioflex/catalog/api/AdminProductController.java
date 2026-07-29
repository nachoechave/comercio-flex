package com.comercioflex.catalog.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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

import com.comercioflex.catalog.application.ProductSearch;
import com.comercioflex.catalog.application.ProductService;
import com.comercioflex.catalog.application.ProductStatusFilter;
import com.comercioflex.identity.application.TenantPermissionGuard;
import com.comercioflex.identity.domain.TenantPermission;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/admin/products")
public class AdminProductController {

	private final ProductService productService;
	private final TenantPermissionGuard permissionGuard;

	public AdminProductController(
			ProductService productService,
			TenantPermissionGuard permissionGuard) {
		this.productService = productService;
		this.permissionGuard = permissionGuard;
	}

	@GetMapping
	ProductPageResponse findAll(
			@RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestParam(defaultValue = "ALL") ProductStatusFilter status,
			@RequestParam(required = false) UUID categoryId,
			@RequestParam(required = false) String q,
			HttpServletRequest request) {
		require(request, TenantPermission.VIEW_CATALOG);
		return ProductPageResponse.from(productService.findPage(
			new ProductSearch(page, size, status, categoryId, q)));
	}

	@PostMapping
	ResponseEntity<ProductDetailResponse> create(
			@Valid @RequestBody CreateProductRequest body,
			HttpServletRequest request) {
		require(request, TenantPermission.MANAGE_CATALOG);
		ProductDetailResponse response =
			ProductDetailResponse.from(productService.create(body.toCommand()));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
			.path("/{id}")
			.buildAndExpand(response.id())
			.toUri();
		return ResponseEntity.created(location).body(response);
	}

	@GetMapping("/{productId}")
	ProductDetailResponse findById(
			@PathVariable UUID productId,
			HttpServletRequest request) {
		require(request, TenantPermission.VIEW_CATALOG);
		return ProductDetailResponse.from(productService.findById(productId));
	}

	@PutMapping("/{productId}")
	ProductDetailResponse update(
			@PathVariable UUID productId,
			@Valid @RequestBody UpdateProductRequest body,
			HttpServletRequest request) {
		require(request, TenantPermission.MANAGE_CATALOG);
		return ProductDetailResponse.from(productService.update(
			productId,
			body.name(),
			body.description(),
			body.categoryId(),
			body.version()));
	}

	@PatchMapping("/{productId}/status")
	ProductDetailResponse changeStatus(
			@PathVariable UUID productId,
			@Valid @RequestBody ChangeProductStatusRequest body,
			HttpServletRequest request) {
		require(request, TenantPermission.MANAGE_CATALOG);
		return ProductDetailResponse.from(productService.changeStatus(
			productId,
			body.status(),
			body.version()));
	}

	@PostMapping("/{productId}/variants")
	ResponseEntity<ProductVariantResponse> addVariant(
			@PathVariable UUID productId,
			@Valid @RequestBody ProductVariantRequest body,
			HttpServletRequest request) {
		require(request, TenantPermission.MANAGE_CATALOG);
		ProductVariantResponse response = ProductVariantResponse.from(
			productService.addVariant(productId, body.toValues()));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
			.path("/{id}")
			.buildAndExpand(response.id())
			.toUri();
		return ResponseEntity.created(location).body(response);
	}

	@PutMapping("/{productId}/variants/{variantId}")
	ProductVariantResponse updateVariant(
			@PathVariable UUID productId,
			@PathVariable UUID variantId,
			@Valid @RequestBody UpdateProductVariantRequest body,
			HttpServletRequest request) {
		require(request, TenantPermission.MANAGE_CATALOG);
		return ProductVariantResponse.from(productService.updateVariant(
			productId,
			variantId,
			body.toValues(),
			body.version()));
	}

	@PatchMapping("/{productId}/variants/{variantId}/status")
	ProductVariantResponse changeVariantStatus(
			@PathVariable UUID productId,
			@PathVariable UUID variantId,
			@Valid @RequestBody ChangeVariantStatusRequest body,
			HttpServletRequest request) {
		require(request, TenantPermission.MANAGE_CATALOG);
		return ProductVariantResponse.from(productService.changeVariantStatus(
			productId,
			variantId,
			body.active(),
			body.version()));
	}

	private void require(HttpServletRequest request, TenantPermission permission) {
		permissionGuard.require(request, permission);
	}
}
