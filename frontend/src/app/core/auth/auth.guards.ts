import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot } from '@angular/router';
import { map } from 'rxjs';

import { AdminRole } from './auth.models';
import { AuthService } from './auth.service';

export function safeReturnUrl(value: string | null): string | null {
  return value?.startsWith('/') && !value.startsWith('//') ? value : null;
}

function loginRedirect(router: Router, returnUrl: string) {
  return router.createUrlTree(['/admin/login'], {
    queryParams: { returnUrl: safeReturnUrl(returnUrl) },
  });
}

export function routeParam(
  route: ActivatedRouteSnapshot,
  name: string,
): string | null {
  let current: ActivatedRouteSnapshot | null = route;
  while (current) {
    const value = current.paramMap.get(name);
    if (value) {
      return value;
    }
    current = current.parent;
  }
  return null;
}

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth
    .loadSession()
    .pipe(
      map((session) =>
        session.authenticated ? true : loginRedirect(router, state.url),
      ),
    );
};

export const adminEntryGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.loadSession().pipe(
    map((session) => {
      if (!session.authenticated) {
        return loginRedirect(router, state.url);
      }
      if (session.memberships.length === 1) {
		const membership = session.memberships[0];
        return router.createUrlTree([
          '/tiendas',
          membership.storeSlug,
          'admin',
		  ...(membership.role === 'STAFF' ? ['pedidos'] : []),
        ]);
      }
      return router.createUrlTree(['/admin/comercios']);
    }),
  );
};

export const membershipSelectionGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.loadSession().pipe(
    map((session) => {
      if (!session.authenticated) {
        return loginRedirect(router, state.url);
      }
      if (session.memberships.length === 1) {
		const membership = session.memberships[0];
        return router.createUrlTree([
          '/tiendas',
          membership.storeSlug,
          'admin',
		  ...(membership.role === 'STAFF' ? ['pedidos'] : []),
        ]);
      }
      return true;
    }),
  );
};

export const membershipGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const storeSlug = routeParam(route, 'storeSlug');

  return auth.loadSession().pipe(
    map((session) => {
      if (!session.authenticated) {
        return loginRedirect(router, state.url);
      }
      if (storeSlug && session.memberships.some((item) => item.storeSlug === storeSlug)) {
        return true;
      }
      return router.createUrlTree(['/admin/comercios'], {
        queryParams: { denied: 'true' },
      });
    }),
  );
};

export function allowedRolesGuard(allowedRoles: readonly AdminRole[]): CanActivateFn {
  return (route, state) => {
    const auth = inject(AuthService);
    const router = inject(Router);
    const storeSlug = routeParam(route, 'storeSlug');

    return auth.loadSession().pipe(
      map((session) => {
        if (!session.authenticated) {
          return loginRedirect(router, state.url);
        }
        const membership = session.memberships.find((item) => item.storeSlug === storeSlug);
        return membership && allowedRoles.includes(membership.role)
          ? true
          : router.createUrlTree(['/admin/comercios'], {
              queryParams: { denied: 'true' },
            });
      }),
    );
  };
}

export const adminHomeGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const storeSlug = routeParam(route, 'storeSlug');
  return auth.loadSession().pipe(
    map((session) => {
      if (!session.authenticated) return router.createUrlTree(['/admin/login']);
      const membership = session.memberships.find((item) => item.storeSlug === storeSlug);
      return membership?.role === 'STAFF'
        ? router.createUrlTree(['/tiendas', storeSlug, 'admin', 'pedidos'])
        : true;
    }),
  );
};
