package com.comercioflex.media.api;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.comercioflex.identity.application.TenantPermissionGuard;
import com.comercioflex.identity.domain.TenantPermission;
import com.comercioflex.media.application.InvalidProductImageException;
import com.comercioflex.media.application.ProductImageService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/admin")
public class AdminProductImageController {

	private final ProductImageService service;
	private final TenantPermissionGuard permissionGuard;

	public AdminProductImageController(
			ProductImageService service,
			TenantPermissionGuard permissionGuard) {
		this.service = service;
		this.permissionGuard = permissionGuard;
	}

	@PutMapping(path = "/products/{productId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ProductImageResponse replace(
			@PathVariable String storeSlug,
			@PathVariable UUID productId,
			@RequestPart("file") MultipartFile file,
			@RequestParam("altText") String altText,
			HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.MANAGE_CATALOG);
		try {
			return ProductImageResponse.admin(storeSlug,
				service.replace(productId, file.getBytes(), altText));
		}
		catch (IOException exception) {
			throw new InvalidProductImageException("No pudimos leer la imagen seleccionada.");
		}
	}

	@DeleteMapping("/products/{productId}/image")
	ResponseEntity<Void> delete(
			@PathVariable UUID productId,
			HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.MANAGE_CATALOG);
		service.delete(productId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/product-images/{imageId}/{size}")
	ResponseEntity<byte[]> load(
			@PathVariable UUID imageId,
			@PathVariable String size,
			HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.VIEW_CATALOG);
		return content(service.load(imageId, ProductImageService.ImageSize.parse(size), false), false);
	}

	static ResponseEntity<byte[]> content(ProductImageService.ImageContent image, boolean publicCache) {
		ResponseEntity.BodyBuilder response = ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(image.contentType()))
			.eTag('"' + image.etag() + '"')
			.header("X-Content-Type-Options", "nosniff");
		response.cacheControl(publicCache
			? CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic().mustRevalidate()
			: CacheControl.noStore());
		return response.body(image.bytes());
	}
}
