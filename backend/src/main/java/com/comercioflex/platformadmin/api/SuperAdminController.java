package com.comercioflex.platformadmin.api;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.identity.application.PlatformRoleGuard;
import com.comercioflex.platformadmin.application.CompanySearch;
import com.comercioflex.platformadmin.application.CompanyService;
import com.comercioflex.platformadmin.application.CompanyProvisioningService;
import com.comercioflex.platformadmin.application.CompanyBrandingService;
import com.comercioflex.platformadmin.application.CompanyNotFoundException;
import com.comercioflex.media.application.InvalidProductImageException;
import com.comercioflex.tenant.domain.BrandAssetType;
import com.comercioflex.platformadmin.application.CompanyStatusFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Validated
@RestController
@RequestMapping("/api/v1/superadmin")
public class SuperAdminController {

	private final CompanyService companyService;
	private final CompanyProvisioningService provisioningService;
	private final CompanyBrandingService brandingService;
	private final PlatformRoleGuard roleGuard;

	public SuperAdminController(
			CompanyService companyService,
			CompanyProvisioningService provisioningService,
			CompanyBrandingService brandingService,
			PlatformRoleGuard roleGuard) {
		this.companyService = companyService;
		this.provisioningService = provisioningService;
		this.brandingService = brandingService;
		this.roleGuard = roleGuard;
	}

	@GetMapping("/companies/{companyId}/branding")
	CompanyBrandingResponse branding(
			@PathVariable UUID companyId,
			HttpServletRequest request) {
		requireSuperAdmin(request);
		return CompanyBrandingResponse.from(brandingService.find(companyId));
	}

	@PutMapping("/companies/{companyId}/branding")
	CompanyBrandingResponse updateBranding(
			@PathVariable UUID companyId,
			@Valid @RequestBody UpdateCompanyBrandingRequest requestBody,
			Authentication authentication,
			HttpServletRequest request) {
		requireSuperAdmin(request);
		return CompanyBrandingResponse.from(brandingService.update(
			companyId, requestBody.toCommand(), principal(authentication)));
	}

	@PutMapping(
		path = "/companies/{companyId}/branding/assets/{assetType}",
		consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	CompanyBrandingResponse replaceBrandingAsset(
			@PathVariable UUID companyId,
			@PathVariable String assetType,
			@RequestPart("file") MultipartFile file,
			Authentication authentication,
			HttpServletRequest request) {
		requireSuperAdmin(request);
		try {
			return CompanyBrandingResponse.from(brandingService.replaceAsset(
				companyId, assetType(assetType), file.getBytes(), principal(authentication)));
		}
		catch (java.io.IOException exception) {
			throw new InvalidProductImageException("No pudimos leer la imagen seleccionada.");
		}
	}

	@DeleteMapping("/companies/{companyId}/branding/assets/{assetType}")
	CompanyBrandingResponse deleteBrandingAsset(
			@PathVariable UUID companyId,
			@PathVariable String assetType,
			Authentication authentication,
			HttpServletRequest request) {
		requireSuperAdmin(request);
		return CompanyBrandingResponse.from(brandingService.deleteAsset(
			companyId, assetType(assetType), principal(authentication)));
	}

	@PostMapping("/companies")
	@ResponseStatus(HttpStatus.CREATED)
	CompanyDetailResponse createCompany(
			@Valid @RequestBody CreateCompanyRequest requestBody,
			Authentication authentication,
			HttpServletRequest request) {
		requireSuperAdmin(request);
		return CompanyDetailResponse.from(provisioningService.create(
			requestBody.toCommand(), principal(authentication)));
	}

	@GetMapping("/dashboard")
	CompanyDashboardResponse dashboard(HttpServletRequest request) {
		requireSuperAdmin(request);
		return CompanyDashboardResponse.from(companyService.dashboard());
	}

	@GetMapping("/companies")
	CompanyPageResponse companies(
			@RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestParam(defaultValue = "ALL") CompanyStatusFilter status,
			@RequestParam(required = false) @Size(max = 100) String q,
			HttpServletRequest request) {
		requireSuperAdmin(request);
		return CompanyPageResponse.from(companyService.findPage(
			new CompanySearch(page, size, status, q)));
	}

	@GetMapping("/companies/{companyId}")
	CompanyDetailResponse company(
			@PathVariable UUID companyId,
			HttpServletRequest request) {
		requireSuperAdmin(request);
		return CompanyDetailResponse.from(companyService.findById(companyId));
	}

	@PostMapping("/companies/{companyId}/activate")
	CompanyDetailResponse activate(
			@PathVariable UUID companyId,
			Authentication authentication,
			HttpServletRequest request) {
		requireSuperAdmin(request);
		return CompanyDetailResponse.from(companyService.activate(
			companyId, principal(authentication)));
	}

	@PostMapping("/companies/{companyId}/suspend")
	CompanyDetailResponse suspend(
			@PathVariable UUID companyId,
			Authentication authentication,
			HttpServletRequest request) {
		requireSuperAdmin(request);
		return CompanyDetailResponse.from(companyService.suspend(
			companyId, principal(authentication)));
	}

	@PostMapping("/companies/{companyId}/retry-provisioning")
	CompanyDetailResponse retryProvisioning(
			@PathVariable UUID companyId,
			Authentication authentication,
			HttpServletRequest request) {
		requireSuperAdmin(request);
		return CompanyDetailResponse.from(provisioningService.retry(
			companyId, principal(authentication)));
	}

	private void requireSuperAdmin(HttpServletRequest request) {
		roleGuard.requireSuperAdmin(request);
	}

	private PlatformPrincipal principal(Authentication authentication) {
		return (PlatformPrincipal) authentication.getPrincipal();
	}

	private BrandAssetType assetType(String value) {
		try {
			return BrandAssetType.parse(value);
		}
		catch (IllegalArgumentException exception) {
			throw new CompanyNotFoundException();
		}
	}
}
