import { isAdminPwaPath } from './admin-pwa.service';

describe('Admin PWA route isolation', () => {
  it('includes platform, tenant admin and superadmin routes', () => {
    expect(isAdminPwaPath('/admin')).toBe(true);
    expect(isAdminPwaPath('/admin/login')).toBe(true);
    expect(isAdminPwaPath('/admin/comercios')).toBe(true);
    expect(isAdminPwaPath('/tiendas/la-ola-madre/admin')).toBe(true);
    expect(isAdminPwaPath('/tiendas/la-ola-madre/admin/pedidos/15')).toBe(true);
    expect(isAdminPwaPath('/superadmin')).toBe(true);
    expect(isAdminPwaPath('/superadmin/empresas')).toBe(true);
    expect(isAdminPwaPath('/superadmin/empresas/12')).toBe(true);
  });

  it('does not turn storefronts or radio sites into admin PWA surfaces', () => {
    expect(isAdminPwaPath('/')).toBe(false);
    expect(isAdminPwaPath('/tiendas/la-ola-madre')).toBe(false);
    expect(isAdminPwaPath('/tiendas/la-ola-madre/carrito')).toBe(false);
    expect(isAdminPwaPath('/atodoboca')).toBe(false);
  });
});
