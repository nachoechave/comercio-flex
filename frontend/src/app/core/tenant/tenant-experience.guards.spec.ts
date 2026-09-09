import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { routes } from '../../app.routes';
import { StorefrontApiService } from '../../features/storefront/storefront-api.service';

@Component({ template: 'Ecommerce' })
class EcommerceStub {}

@Component({ template: 'Radio' })
class RadioStub {}

describe('tenant experience composition', () => {
  const api = { getSettings: vi.fn(), resolveStorefront: vi.fn() };

  beforeEach(() => {
    api.getSettings.mockReset();
    api.resolveStorefront.mockReset();
    api.resolveStorefront.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    // Preserve the application's actual matching order and guards; replace only visual trees.
    const publicRoutes = routes.filter(r => r.path === 'tiendas/:storeSlug' || (r.path === '' && r.canMatch));
    TestBed.configureTestingModule({ providers: [
      { provide: StorefrontApiService, useValue: api },
      provideRouter(publicRoutes.map(r => ({
        path: r.path,
        canMatch: r.canMatch,
        children: [{ path: '**', component: r.loadComponent ? EcommerceStub : RadioStub }],
      }))),
    ] });
  });

  it.each([undefined, 'ECOMMERCE', 'RADIO'])('selects the slug experience for %s', async tenantType => {
    api.getSettings.mockReturnValue(of({ tenantType }));
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/tiendas/example', tenantType === 'RADIO' ? RadioStub : EcommerceStub);
    expect(api.getSettings).toHaveBeenCalledWith('example');
  });

  it.each([undefined, 'ECOMMERCE', 'RADIO'])('selects the custom-domain experience for %s', async tenantType => {
    api.resolveStorefront.mockReturnValue(of({ storeSlug: 'example', displayName: 'Example', tenantType }));
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/', tenantType === 'RADIO' ? RadioStub : EcommerceStub);
    expect(api.getSettings).not.toHaveBeenCalled();
  });

  it('keeps ecommerce cart paths in the ecommerce route tree', async () => {
    api.getSettings.mockReturnValue(of({ tenantType: 'ECOMMERCE' }));
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/tiendas/example/carrito', EcommerceStub);
    expect(TestBed.inject(Router).url).toBe('/tiendas/example/carrito');
  });

  it('does not fall back to ecommerce when tenant resolution fails', async () => {
    api.getSettings.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    const harness = await RouterTestingHarness.create();
    await expect(harness.navigateByUrl('/tiendas/example')).rejects.toBeTruthy();
    expect(harness.routeNativeElement).toBeNull();
  });
});
