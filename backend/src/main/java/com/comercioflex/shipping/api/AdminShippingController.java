package com.comercioflex.shipping.api;

import com.comercioflex.identity.application.TenantPermissionGuard;
import com.comercioflex.identity.domain.TenantPermission;
import com.comercioflex.shipping.application.ShippingService;
import com.comercioflex.shipping.domain.ShippingModels.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/admin")
public class AdminShippingController {
  private final ShippingService shipping;
  private final TenantPermissionGuard permissions;

  public AdminShippingController(ShippingService shipping, TenantPermissionGuard permissions) {
    this.shipping = shipping;
    this.permissions = permissions;
  }

  @GetMapping("/shipping")
  Settings settings(HttpServletRequest request) {
    ShippingAccess.requireEcommerce(request);
    permissions.require(request, TenantPermission.MANAGE_BASIC_SETTINGS);
    return shipping.settings();
  }

  @PutMapping("/shipping")
  Settings save(@Valid @RequestBody Settings body, HttpServletRequest request) {
    ShippingAccess.requireEcommerce(request);
    permissions.require(request, TenantPermission.MANAGE_BASIC_SETTINGS);
    return shipping.save(body);
  }

  @GetMapping("/orders/{id}/shipment")
  Shipment shipment(@PathVariable UUID id, HttpServletRequest request) {
    ShippingAccess.requireEcommerce(request);
    permissions.require(request, TenantPermission.MANAGE_ORDERS);
    return shipping.shipment(id);
  }

  @PutMapping("/orders/{id}/shipment")
  Shipment update(
      @PathVariable UUID id, @Valid @RequestBody UpdateShipment body, HttpServletRequest request) {
    ShippingAccess.requireEcommerce(request);
    permissions.require(request, TenantPermission.MANAGE_ORDERS);
    return shipping.update(id, body);
  }
}
