import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { StorefrontApiService } from '../../features/storefront/storefront-api.service';

const reserved = new Set('no-encontrado api admin superadmin tiendas stores login logout auth oauth actuator error assets static registro ingresar olvide-contrasena nueva-contrasena mi-cuenta socios programas nosotros carrito checkout mis-pedidos pedidos productos payment-return'.split(' '));
export const radioCleanGuard: CanMatchFn = (_route, segments) => {
  const slug = segments[0]?.path ?? '';
  if (reserved.has(slug) || slug.length > 100 || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(slug)) return false;
  const router = inject(Router);
  return inject(StorefrontApiService).getSettings(slug).pipe(
    map(settings => settings.tenantType === 'RADIO'),
    catchError(() => of(router.createUrlTree(['/no-encontrado']))),
  );
};
