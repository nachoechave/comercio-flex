package com.comercioflex.platformadmin.api;

import java.util.UUID;

import com.comercioflex.identity.application.PlatformRoleGuard;
import com.comercioflex.platformadmin.application.PlatformBillingService;
import com.comercioflex.platformadmin.application.PlatformBillingService.BillingMonth;
import com.comercioflex.platformadmin.application.PlatformBillingService.BillingRow;
import com.comercioflex.platformadmin.application.PlatformBillingService.SavePayment;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/superadmin/billing")
public class SuperAdminBillingController {

  private final PlatformBillingService billing;
  private final PlatformRoleGuard roles;

  public SuperAdminBillingController(PlatformBillingService billing, PlatformRoleGuard roles) {
    this.billing = billing;
    this.roles = roles;
  }

  @GetMapping
  BillingMonth month(
      @RequestParam int year,
      @RequestParam int month,
      HttpServletRequest request) {
    roles.requireSuperAdmin(request);
    return billing.month(year, month);
  }

  @PutMapping("/{companyId}")
  BillingRow save(
      @PathVariable UUID companyId,
      @RequestBody SavePayment body,
      HttpServletRequest request) {
    roles.requireSuperAdmin(request);
    return billing.save(companyId, body);
  }
}
