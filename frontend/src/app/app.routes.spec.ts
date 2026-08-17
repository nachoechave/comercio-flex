import { AdminLayout } from './layouts/admin-layout/admin-layout';
import { StorefrontLayout } from './layouts/storefront-layout/storefront-layout';
import { routes } from './app.routes';

describe('application routes', () => {
  it('keeps the specific admin route ahead of the public storefront route', async () => {
    const adminIndex = routes.findIndex((route) => route.path === 'tiendas/:storeSlug/admin');
    const storefrontIndex = routes.findIndex((route) => route.path === 'tiendas/:storeSlug');
    const adminRoute = routes[adminIndex];
    const storefrontRoute = routes[storefrontIndex];

    expect(adminIndex).toBeGreaterThanOrEqual(0);
    expect(storefrontIndex).toBeGreaterThan(adminIndex);
    expect(await adminRoute.loadComponent?.()).toBe(AdminLayout);
    expect(await storefrontRoute.loadComponent?.()).toBe(StorefrontLayout);
  });

  it('lazy-loads owner-only payment settings under the admin store', async () => {
    const adminRoute = routes.find((route) => route.path === 'tiendas/:storeSlug/admin');
    const paymentRoute = adminRoute?.children?.find(
      (route) => route.path === 'configuracion/pagos',
    );

    expect(paymentRoute).toBeTruthy();
    expect(paymentRoute?.canActivate).toHaveLength(1);
    expect(await paymentRoute?.loadChildren?.()).toEqual(
      expect.arrayContaining([expect.objectContaining({ path: '' })]),
    );
  });

  it('exposes the dedicated public payment return route', async () => {
    const returnRoute = routes.find(
      (route) => route.path === 'stores/:storeSlug/payment-return/:returnToken',
    );

    expect(returnRoute).toBeTruthy();
    expect(await returnRoute?.loadComponent?.()).toBeTruthy();
  });

  it('lazy-loads the isolated SuperAdmin area', async () => {
    const route = routes.find((candidate) => candidate.path === 'superadmin');

    expect(route).toBeTruthy();
    expect(route?.canActivate).toHaveLength(2);
    expect(route?.canActivateChild).toHaveLength(2);
    expect(route?.children?.map((child) => child.path)).toEqual([
      '',
      'empresas',
      'empresas/nueva',
      'empresas/:companyId/apariencia',
      'empresas/:companyId',
    ]);
    expect(await route?.loadComponent?.()).toBeTruthy();
  });
});
