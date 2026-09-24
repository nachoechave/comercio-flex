import { isAdminPwaPath } from './admin-pwa.service';

describe('Admin PWA route isolation', () => {
  it('includes platform and tenant admin routes', () => {
    expect(isAdminPwaPath('/admin')).toBe(true);
    expect(isAdminPwaPath('/admin/login')).toBe(true);
    expect(isAdminPwaPath('/admin/comercios')).toBe(true);
    expect(isAdminPwaPath('/tiendas/la-ola-madre/admin')).toBe(true);
    expect(isAdminPwaPath('/tiendas/la-ola-madre/admin/pedidos/15')).toBe(true);
  });

  it('does not turn storefronts, radio sites or superadmin into admin PWA surfaces', () => {
    expect(isAdminPwaPath('/')).toBe(false);
    expect(isAdminPwaPath('/tiendas/la-ola-madre')).toBe(false);
    expect(isAdminPwaPath('/tiendas/la-ola-madre/carrito')).toBe(false);
    expect(isAdminPwaPath('/atodoboca')).toBe(false);
    expect(isAdminPwaPath('/superadmin')).toBe(false);
  });
});
