import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { StorefrontApiService } from './storefront-api.service';
import { storefrontDomainGuard } from './storefront-domain.guard';

@Component({
  standalone: true,
  template: 'Storefront',
})
class StorefrontStub {}

@Component({
  standalone: true,
  template: 'Carrito',
})
class CartStub {}

@Component({
  standalone: true,
  template: 'Admin plataforma',
})
class PlatformAdminStub {}

describe('storefrontDomainGuard routing', () => {
  let router: Router;

  const api = {
    resolveStorefront: vi.fn(),
  };

  beforeEach(() => {
    api.resolveStorefront.mockReset();

    TestBed.configureTestingModule({
      providers: [
        {
          provide: StorefrontApiService,
          useValue: api,
        },
        provideRouter([
          {
            path: '',
            canMatch: [storefrontDomainGuard],
            children: [
              {
                path: '',
                pathMatch: 'full',
                component: StorefrontStub,
              },
              {
                path: 'carrito',
                component: CartStub,
              },
              {
                path: '**',
                redirectTo: '',
              },
            ],
          },
          {
            path: 'admin',
            component: PlatformAdminStub,
          },
        ]),
      ],
    });

    router = TestBed.inject(Router);
  });

  it('keeps /admin inside a custom-domain storefront instead of opening platform admin', async () => {
    api.resolveStorefront.mockReturnValue(
      of({
        storeSlug: 'la-ola-madre',
        displayName: 'La Ola Madre',
      }),
    );

    const navigated = await router.navigateByUrl('/admin');

    expect(navigated).toBe(true);
    expect(router.url).toBe('/');
  });

  it('allows /carrito directly on a custom domain', async () => {
    api.resolveStorefront.mockReturnValue(
      of({
        storeSlug: 'la-ola-madre',
        displayName: 'La Ola Madre',
      }),
    );

    const navigated = await router.navigateByUrl('/carrito');

    expect(navigated).toBe(true);
    expect(router.url).toBe('/carrito');
  });

  it('allows the platform /admin route when hostname resolution returns 404', async () => {
    api.resolveStorefront.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 404,
            statusText: 'Not Found',
          }),
      ),
    );

    const navigated = await router.navigateByUrl('/admin');

    expect(navigated).toBe(true);
    expect(router.url).toBe('/admin');
  });
});