package com.comercioflex.media.api;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.media.application.ProductImageNotFoundException;
import com.comercioflex.tenant.application.BrandingAssetService;
import com.comercioflex.tenant.domain.BrandAssetType;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/media/branding")
public class PublicBrandingAssetController {

	private final BrandingAssetService service;

	public PublicBrandingAssetController(BrandingAssetService service) {
		this.service = service;
	}

	@GetMapping("/{assetType}")
	ResponseEntity<byte[]> load(@PathVariable String assetType) {
		BrandAssetType type;
		try {
			type = BrandAssetType.parse(assetType);
		}
		catch (IllegalArgumentException exception) {
			throw new ProductImageNotFoundException();
		}
		var asset = service.load(type);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(asset.contentType()))
			.eTag('"' + asset.etag() + '"')
			.header("X-Content-Type-Options", "nosniff")
			.cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic().mustRevalidate())
			.body(asset.bytes());
	}
}
