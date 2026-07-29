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
});
