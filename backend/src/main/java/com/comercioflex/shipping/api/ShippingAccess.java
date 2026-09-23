package com.comercioflex.shipping.api;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.comercioflex.tenant.api.TenantResolutionFilter;
import com.comercioflex.tenant.application.ResolvedTenant;
import com.comercioflex.tenant.domain.TenantType;
final class ShippingAccess {
 private ShippingAccess() {}
 static void requireEcommerce(HttpServletRequest request) {
  Object value=request.getAttribute(TenantResolutionFilter.RESOLVED_TENANT_ATTRIBUTE);
  if(!(value instanceof ResolvedTenant tenant) || tenant.tenantType()!=TenantType.ECOMMERCE)
   throw new ResponseStatusException(HttpStatus.NOT_FOUND);
 }
}
