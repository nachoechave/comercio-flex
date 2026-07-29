package com.comercioflex.catalog.api;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.catalog.application.PublicCatalogSearch;
import com.comercioflex.catalog.application.PublicCatalogService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Validated
@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/catalog")
public class PublicCatalogController {

	private static final String SLUG_PATTERN =
		"[a-z0-9](?:[a-z0-9-]{0,178}[a-z0-9])?";

	private final PublicCatalogService catalogService;

	public PublicCatalogController(PublicCatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@GetMapping("/categories")
	ResponseEntity<List<PublicCategoryResponse>> findCategories() {
		List<PublicCategoryResponse> response = catalogService.findCategories()
			.stream()
			.map(PublicCategoryResponse::from)
			.toList();
		return noStore(response);
	}

	@GetMapping("/products")
	ResponseEntity<PublicProductPageResponse> findProducts(
			@RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
			@RequestParam(defaultValue = "24") @Min(1) @Max(60) int size,
			@RequestParam(required = false) @Size(max = 100) String q,
			@RequestParam(required = false)
			@Pattern(regexp = SLUG_PATTERN) String category) {
		PublicProductPageResponse response = PublicProductPageResponse.from(
			catalogService.findProducts(
				new PublicCatalogSearch(page, size, q, category)));
		return noStore(response);
	}

	@GetMapping("/products/{productSlug}")
	ResponseEntity<PublicProductDetailResponse> findProduct(
			@PathVariable @Pattern(regexp = SLUG_PATTERN) String productSlug) {
		return noStore(PublicProductDetailResponse.from(
			catalogService.findProduct(productSlug)));
	}

	private <T> ResponseEntity<T> noStore(T body) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.body(body);
	}
}
