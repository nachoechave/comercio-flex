import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, ResolveFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { routeParam } from '../auth/auth.guards';
import { StorefrontApiService } from '../../features/storefront/storefront-api.service';
import { StoreSettings } from '../../features/storefront/storefront.models';
export const adminTenantSettings: ResolveFn<StoreSettings> = route => inject(StorefrontApiService).getSettings(routeParam(route, 'storeSlug')!);
export const radioAdminGuard: CanActivateFn = route => {
 const router = inject(Router); const slug = routeParam(route, 'storeSlug')!;
 return inject(StorefrontApiService).getSettings(slug).pipe(map(s => s.tenantType === 'RADIO' ? true : router.createUrlTree(['/tiendas', slug, 'admin'])));
};
