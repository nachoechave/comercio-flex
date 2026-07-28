import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  convertToParamMap,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { firstValueFrom, Observable, of } from 'rxjs';

import { AuthenticatedSession, CurrentSession } from './auth.models';
import { adminEntryGuard, authGuard, membershipGuard, safeReturnUrl } from './auth.guards';
import { AuthService } from './auth.service';

const SESSION: AuthenticatedSession = {
  authenticated: true,
  user: { id: 'user-1', email: 'owner@example.com', displayName: 'Dueña Demo' },
  memberships: [
    { storeSlug: 'tienda-a', storeName: 'Tienda A', role: 'OWNER' },
  ],
};

describe('authentication guards', () => {
  let session: CurrentSession;
  let router: Router;

  beforeEach(() => {
    session = SESSION;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            loadSession: () => of(session),
          },
        },
      ],
    });
    router = TestBed.inject(Router);
  });

  it('rejects external return URLs', () => {
    expect(safeReturnUrl('//attacker.example')).toBeNull();
    expect(safeReturnUrl('https://attacker.example')).toBeNull();
    expect(safeReturnUrl('/tiendas/tienda-a/admin')).toBe('/tiendas/tienda-a/admin');
  });

  it('redirects an anonymous visitor to login', async () => {
    session = { authenticated: false };

    const result = TestBed.runInInjectionContext(() =>
      authGuard(
        {} as ActivatedRouteSnapshot,
        { url: '/tiendas/tienda-a/admin' } as RouterStateSnapshot,
      ),
    ) as Observable<boolean | UrlTree>;

    const urlTree = (await firstValueFrom(result)) as UrlTree;
    expect(router.serializeUrl(urlTree)).toContain('/admin/login');
    expect(router.serializeUrl(urlTree)).toContain('returnUrl=');
  });

  it('sends a user with one membership directly to its panel', async () => {
    const result = TestBed.runInInjectionContext(() =>
      adminEntryGuard(
        {} as ActivatedRouteSnapshot,
        { url: '/admin' } as RouterStateSnapshot,
      ),
    ) as Observable<boolean | UrlTree>;

    expect(router.serializeUrl((await firstValueFrom(result)) as UrlTree)).toBe(
      '/tiendas/tienda-a/admin',
    );
  });

  it('does not allow a membership from another store', async () => {
    const route = {
      paramMap: convertToParamMap({ storeSlug: 'tienda-b' }),
    } as ActivatedRouteSnapshot;

    const result = TestBed.runInInjectionContext(() =>
      membershipGuard(route, {
        url: '/tiendas/tienda-b/admin',
      } as RouterStateSnapshot),
    ) as Observable<boolean | UrlTree>;

    expect(router.serializeUrl((await firstValueFrom(result)) as UrlTree)).toContain(
      '/admin/comercios?denied=true',
    );
  });
});
