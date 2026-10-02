package com.comercioflex.promotion.api;

import java.time.Instant;
import java.util.List;

import com.comercioflex.promotion.application.QuantityPromotionService;
import com.comercioflex.promotion.application.QuantityPromotionService.View;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/catalog/promotions")
public class PublicQuantityPromotionController {

  private final QuantityPromotionService service;

  public PublicQuantityPromotionController(QuantityPromotionService service) {
    this.service = service;
  }

  @GetMapping
  List<View> active() {
    return service.active(Instant.now());
  }
}
