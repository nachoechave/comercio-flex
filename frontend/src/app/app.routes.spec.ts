import { AdminLayout } from './layouts/admin-layout/admin-layout';
import { StorefrontLayout } from './layouts/storefront-layout/storefront-layout';
import { LandingPage } from './features/landing/landing-page/landing-page';
import { routes } from './app.routes';

describe('application routes', () => {
  it('keeps radio in an independent lazy route tree after tenant administration', async () => {
    const storefronts = routes.filter(route => route.path === 'tiendas/:storeSlug');
    expect(storefronts).toHaveLength(2);
    expect(storefronts[0].canMatch).toHaveLength(1);
    expect(storefronts[1].canMatch).toHaveLength(1);
    expect(storefronts[1].loadComponent).toBeUndefined();
    const { RADIO_ROUTES } = await import('./features/radio/radio.routes');
    expect(await storefronts[1].loadChildren?.()).toEqual(RADIO_ROUTES);
    expect(RADIO_ROUTES.map(route => route.path)).toEqual(['']);
    expect(RADIO_ROUTES[0].children?.map(route => route.path)).toEqual([
      'login', '', 'programas', 'nosotros', 'registro', 'ingresar', 'olvide-contrasena', 'nueva-contrasena', 'socios', 'mi-cuenta', '**',
    ]);
    const account = RADIO_ROUTES[0].children?.find(route => route.path === 'mi-cuenta');
    expect(account?.canActivate).toHaveLength(1);
    expect(account?.canActivateChild).toHaveLength(1);
  });
  it('loads the commercial landing only at the public root', async () => {
    const rootRoute = routes.find(
      (route) => route.path === '' && route.pathMatch === 'full',
    );

    expect(rootRoute?.pathMatch).toBe('full');
    expect(await rootRoute?.loadComponent?.()).toBe(LandingPage);
  });

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

  it('keeps login, storefront and tenant admin paths independent from the landing', () => {
    expect(routes.find((route) => route.path === 'admin/login')).toBeTruthy();
    expect(routes.find((route) => route.path === 'tiendas/:storeSlug')).toBeTruthy();
    expect(routes.find((route) => route.path === 'tiendas/:storeSlug/admin')).toBeTruthy();
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

  it('keeps unknown custom-domain paths inside the storefront', async () => {
    const customDomainRoute = routes.find(
      (route) =>
        route.path === '' &&
        route.canMatch?.length === 1 &&
        route.loadChildren,
    );

    expect(customDomainRoute).toBeTruthy();

    const storefrontRoutes = await customDomainRoute?.loadChildren?.();

    expect(Array.isArray(storefrontRoutes)).toBe(true);

    if (!Array.isArray(storefrontRoutes)) {
      throw new Error('Expected storefront routes to be an array');
    }

    expect(storefrontRoutes.at(-1)).toEqual(
      expect.objectContaining({
        path: '**',
        redirectTo: '',
      }),
    );
  });
});
