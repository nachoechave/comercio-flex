package com.comercioflex.shipping.api;

import com.comercioflex.identity.application.TenantPermissionGuard;
import com.comercioflex.identity.domain.TenantPermission;
import com.comercioflex.shipping.application.CarrierShippingService;
import com.comercioflex.shipping.domain.CarrierModels.SaveSettings;
import com.comercioflex.shipping.domain.CarrierModels.SettingsView;
import com.comercioflex.shipping.domain.ShippingModels.Shipment;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/admin")
public class CarrierShippingController {
  private final CarrierShippingService carrier;
  private final TenantPermissionGuard permissions;

  public CarrierShippingController(
      CarrierShippingService carrier, TenantPermissionGuard permissions) {
    this.carrier = carrier;
    this.permissions = permissions;
  }

  @GetMapping("/shipping/carrier")
  SettingsView settings(HttpServletRequest request) {
    ShippingAccess.requireEcommerce(request);
    permissions.require(request, TenantPermission.MANAGE_BASIC_SETTINGS);
    return carrier.settings();
  }

  @PutMapping("/shipping/carrier")
  SettingsView save(@Valid @RequestBody SaveSettings body, HttpServletRequest request) {
    ShippingAccess.requireEcommerce(request);
    permissions.require(request, TenantPermission.MANAGE_BASIC_SETTINGS);
    return carrier.save(body);
  }

  @PostMapping("/orders/{id}/shipment/carrier")
  Shipment provision(@PathVariable UUID id, HttpServletRequest request) {
    ShippingAccess.requireEcommerce(request);
    permissions.require(request, TenantPermission.MANAGE_ORDERS);
    return carrier.provision(id);
  }

  @GetMapping(value = "/orders/{id}/shipment/label", produces = MediaType.APPLICATION_PDF_VALUE)
  ResponseEntity<byte[]> label(@PathVariable UUID id, HttpServletRequest request) {
    ShippingAccess.requireEcommerce(request);
    permissions.require(request, TenantPermission.MANAGE_ORDERS);
    byte[] label = carrier.label(id);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("etiqueta-envio-" + id + ".pdf").build().toString())
        .cacheControl(CacheControl.noStore())
        .body(label);
  }
}
