import { inject } from '@angular/core';
import { CanMatchFn } from '@angular/router';
import { map } from 'rxjs';
import { StorefrontApiService } from '../../features/storefront/storefront-api.service';
import { StorefrontRoutingService } from '../../features/storefront/storefront-routing.service';
import { TenantType } from './tenant-type';

// Failures propagate: an unavailable or unknown tenant never falls through to another experience.
export function tenantExperienceGuard(type: TenantType): CanMatchFn {
  return (_route, segments) => inject(StorefrontApiService).getSettings(segments[1].path).pipe(
    map(settings => (settings.tenantType ?? 'ECOMMERCE') === type),
  );
}

export const radioDomainGuard: CanMatchFn = (_route, segments) => {
  const routing = inject(StorefrontRoutingService);
  if (['admin', 'superadmin', 'tiendas', 'stores'].includes(segments[0]?.path)) return false;
  return routing.resolveCustomDomain().pipe(map(resolved => resolved && routing.tenantType() === 'RADIO'));
};
