package com.comercioflex.promotion.api;

import java.util.List;
import java.util.UUID;

import com.comercioflex.identity.application.TenantPermissionGuard;
import com.comercioflex.identity.domain.TenantPermission;
import com.comercioflex.promotion.application.QuantityPromotionService;
import com.comercioflex.promotion.application.QuantityPromotionService.Command;
import com.comercioflex.promotion.application.QuantityPromotionService.View;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/admin/promotions")
public class AdminQuantityPromotionController {

  private final QuantityPromotionService service;
  private final TenantPermissionGuard permissions;

  public AdminQuantityPromotionController(
      QuantityPromotionService service,
      TenantPermissionGuard permissions) {
    this.service = service;
    this.permissions = permissions;
  }

  @GetMapping
  List<View> list(HttpServletRequest request) {
    permissions.require(request, TenantPermission.VIEW_CATALOG);
    return service.list();
  }

  @PostMapping
  ResponseEntity<View> create(@RequestBody Command body, HttpServletRequest request) {
    permissions.require(request, TenantPermission.MANAGE_CATALOG);
    return ResponseEntity.status(201).body(service.create(body));
  }

  @PutMapping("/{promotionId}")
  View update(
      @PathVariable UUID promotionId,
      @RequestBody UpdateRequest body,
      HttpServletRequest request) {
    permissions.require(request, TenantPermission.MANAGE_CATALOG);
    return service.update(promotionId, body.command(), body.version());
  }

  @PatchMapping("/{promotionId}/status")
  View status(
      @PathVariable UUID promotionId,
      @RequestBody StatusRequest body,
      HttpServletRequest request) {
    permissions.require(request, TenantPermission.MANAGE_CATALOG);
    return service.setActive(promotionId, body.active(), body.version());
  }

  public record UpdateRequest(
      String scopeType,
      UUID productId,
      java.util.List<UUID> productIds,
      java.util.List<UUID> secondProductIds,
      UUID categoryId,
      String name,
      int bundleQuantity,
      java.math.BigDecimal bundlePrice,
      boolean active,
      java.time.Instant startsAt,
      java.time.Instant endsAt,
      long version) {
    Command command() {
      return new Command(
          scopeType, productId, productIds, secondProductIds, categoryId, name,
          bundleQuantity, bundlePrice, active, startsAt, endsAt);
    }
  }

  public record StatusRequest(boolean active, long version) {}
}
