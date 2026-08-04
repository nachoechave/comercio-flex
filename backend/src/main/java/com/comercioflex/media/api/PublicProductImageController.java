package com.comercioflex.media.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.media.application.ProductImageService;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/media/product-images")
public class PublicProductImageController {

	private final ProductImageService service;

	public PublicProductImageController(ProductImageService service) {
		this.service = service;
	}

	@GetMapping("/{imageId}/{size}")
	ResponseEntity<byte[]> load(@PathVariable UUID imageId, @PathVariable String size) {
		return AdminProductImageController.content(
			service.load(imageId, ProductImageService.ImageSize.parse(size), true), true);
	}
}
