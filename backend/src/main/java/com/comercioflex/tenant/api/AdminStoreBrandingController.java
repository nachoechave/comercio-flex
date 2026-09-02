package com.comercioflex.tenant.api;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.comercioflex.identity.application.TenantPermissionGuard;
import com.comercioflex.identity.domain.TenantPermission;
import com.comercioflex.media.application.InvalidProductImageException;
import com.comercioflex.tenant.application.BrandingAssetService;
import com.comercioflex.tenant.application.TenantBrandingService;
import com.comercioflex.tenant.domain.BrandAssetType;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/admin/branding")
public class AdminStoreBrandingController {

	private final TenantBrandingService branding;
	private final BrandingAssetService assets;
	private final TenantPermissionGuard permissionGuard;

	public AdminStoreBrandingController(
			TenantBrandingService branding,
			BrandingAssetService assets,
			TenantPermissionGuard permissionGuard) {
		this.branding = branding;
		this.assets = assets;
		this.permissionGuard = permissionGuard;
	}

	@GetMapping
	StoreSettingsResponse.BrandingResponse find(
			@PathVariable String storeSlug, HttpServletRequest request) {
		requireManageSettings(request);
		return StoreSettingsResponse.BrandingResponse.from(storeSlug, branding.findCurrent());
	}

	@PutMapping
	StoreSettingsResponse.BrandingResponse update(
			@PathVariable String storeSlug,
			@Valid @RequestBody UpdateStoreBrandingRequest body,
			HttpServletRequest request) {
		requireManageSettings(request);
		return StoreSettingsResponse.BrandingResponse.from(
			storeSlug, branding.update(body.toCommand()));
	}

	@PutMapping(path = "/assets/{assetType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	StoreSettingsResponse.BrandingResponse replaceAsset(
			@PathVariable String storeSlug,
			@PathVariable String assetType,
			@RequestPart("file") MultipartFile file,
			HttpServletRequest request) {
		requireManageSettings(request);
		try {
			assets.replace(parseAssetType(assetType), file.getBytes());
			return StoreSettingsResponse.BrandingResponse.from(
				storeSlug, branding.findCurrent());
		}
		catch (IOException exception) {
			throw new InvalidProductImageException("No pudimos leer la imagen seleccionada.");
		}
	}

	@DeleteMapping("/assets/{assetType}")
	StoreSettingsResponse.BrandingResponse deleteAsset(
			@PathVariable String storeSlug,
			@PathVariable String assetType,
			HttpServletRequest request) {
		requireManageSettings(request);
		assets.delete(parseAssetType(assetType));
		return StoreSettingsResponse.BrandingResponse.from(storeSlug, branding.findCurrent());
	}

	private void requireManageSettings(HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.MANAGE_BASIC_SETTINGS);
	}

	private BrandAssetType parseAssetType(String value) {
		try {
			return BrandAssetType.parse(value);
		}
		catch (IllegalArgumentException exception) {
			throw new InvalidProductImageException("El tipo de imagen no es válido.");
		}
	}
}
