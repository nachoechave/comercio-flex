import { map } from 'rxjs';
import { inject } from '@angular/core';
import { CanMatchFn } from '@angular/router';

import { StorefrontRoutingService } from './storefront-routing.service';

export const storefrontDomainGuard: CanMatchFn = () => {
  const routing = inject(StorefrontRoutingService);

  return routing.resolveCustomDomain().pipe(map((resolved) => resolved && routing.tenantType() === 'ECOMMERCE'));
};