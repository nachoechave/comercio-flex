import { Routes } from '@angular/router';

import {
  adminEntryGuard,
  adminHomeGuard,
  allowedRolesGuard,
  authGuard,
  membershipGuard,
  membershipSelectionGuard,
} from './core/auth/auth.guards';
import { ADMIN_ROLES } from './core/auth/auth.models';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./features/storefront/home/storefront-home').then((module) => module.StorefrontHome),
  },
  {
    path: 'admin/login',
    loadComponent: () => import('./features/auth/login/login').then((module) => module.Login),
  },
  {
    path: 'admin/comercios',
    canActivate: [authGuard, membershipSelectionGuard],
    loadComponent: () =>
      import('./features/admin/store-selector/store-selector').then(
        (module) => module.StoreSelector,
      ),
  },
  {
    path: 'admin',
    pathMatch: 'full',
    canActivate: [adminEntryGuard],
    loadComponent: () =>
      import('./features/admin/store-selector/store-selector').then(
        (module) => module.StoreSelector,
      ),
  },
  {
    path: 'stores/:storeSlug/payment-return/:returnToken',
    loadComponent: () =>
      import('./features/storefront/payment/payment-return-page/payment-return-page').then(
        (module) => module.PaymentReturnPage,
      ),
  },
  {
    path: 'tiendas/:storeSlug/admin',
    canActivate: [authGuard, membershipGuard, allowedRolesGuard(ADMIN_ROLES)],
    canActivateChild: [authGuard, membershipGuard],
    loadComponent: () =>
      import('./layouts/admin-layout/admin-layout').then((module) => module.AdminLayout),
    children: [
      {
        path: '',
        canActivate: [adminHomeGuard],
        loadComponent: () =>
          import('./features/admin/dashboard/admin-dashboard').then(
            (module) => module.AdminDashboard,
          ),
      },
      {
        path: 'configuracion/comercio',
        canActivate: [allowedRolesGuard(['OWNER', 'ADMIN'])],
        loadComponent: () =>
          import('./features/admin/store-settings/store-settings-page').then(
            (module) => module.StoreSettingsPage,
          ),
      },
      {
        path: 'categorias',
        loadChildren: () =>
          import('./features/admin/categories/category.routes').then(
            (module) => module.CATEGORY_ROUTES,
          ),
      },
      {
        path: 'productos',
        loadChildren: () =>
          import('./features/admin/products/product.routes').then(
            (module) => module.PRODUCT_ROUTES,
          ),
      },
      {
        path: 'inventario',
        loadChildren: () =>
          import('./features/admin/inventory/inventory.routes').then(
            (module) => module.INVENTORY_ROUTES,
          ),
      },
      {
        path: 'pedidos',
        loadChildren: () =>
          import('./features/admin/orders/order.routes').then((module) => module.ORDER_ROUTES),
      },
      {
        path: 'configuracion/pagos',
        canActivate: [allowedRolesGuard(['OWNER'])],
        loadChildren: () =>
          import('./features/admin/payment-connection/payment-connection.routes').then(
            (module) => module.PAYMENT_CONNECTION_ROUTES,
          ),
      },
    ],
  },
  {
    path: 'tiendas/:storeSlug',
    loadComponent: () =>
      import('./layouts/storefront-layout/storefront-layout').then(
        (module) => module.StorefrontLayout,
      ),
    loadChildren: () =>
      import('./features/storefront/storefront.routes').then((module) => module.STOREFRONT_ROUTES),
  },
  { path: '**', redirectTo: '' },
];
