import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { routeParam } from '../../core/auth/auth.guards';
import { StorefrontRoutingService } from '../storefront/storefront-routing.service';
import { RadioRoutingService } from './radio-routing.service';

export const radioAccountGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const routing = inject(RadioRoutingService);
  const slug = routeParam(route, 'storeSlug') ?? '';
  return auth.loadSession(true).pipe(map(session => session.authenticated
    ? true : router.createUrlTree(routing.route(slug, 'ingresar'))));
};
