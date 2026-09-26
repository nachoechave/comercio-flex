package com.comercioflex.analytics.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.comercioflex.analytics.application.AnalyticsService;
import com.comercioflex.identity.application.TenantPermissionGuard;
import com.comercioflex.identity.domain.TenantPermission;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/admin/analytics")
public class AdminAnalyticsController {

    private final AnalyticsService service;
    private final TenantPermissionGuard permissionGuard;

    public AdminAnalyticsController(
            AnalyticsService service,
            TenantPermissionGuard permissionGuard) {
        this.service = service;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    AnalyticsService.Summary summary(
            @RequestParam(defaultValue = "7") int days,
            HttpServletRequest request) {
        permissionGuard.require(request, TenantPermission.VIEW_DASHBOARD);
        if (days != 1 && days != 7 && days != 30) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El período debe ser 1, 7 o 30 días.");
        }
        return service.summary(days);
    }
}
