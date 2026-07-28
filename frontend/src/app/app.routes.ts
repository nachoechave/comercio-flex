import { Routes } from '@angular/router';

import {
  adminEntryGuard,
  allowedRolesGuard,
  authGuard,
  membershipGuard,
  membershipSelectionGuard,
} from './core/auth/auth.guards';
import { ADMIN_ROLES } from './core/auth/auth.models';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./layouts/storefront-layout/storefront-layout').then(
        (module) => module.StorefrontLayout,
      ),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/storefront/home/storefront-home').then(
            (module) => module.StorefrontHome,
          ),
      },
    ],
  },
  {
    path: 'admin/login',
    loadComponent: () =>
      import('./features/auth/login/login').then((module) => module.Login),
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
    path: 'tiendas/:storeSlug/admin',
    canActivate: [authGuard, membershipGuard, allowedRolesGuard(ADMIN_ROLES)],
    loadComponent: () =>
      import('./layouts/admin-layout/admin-layout').then((module) => module.AdminLayout),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/admin/dashboard/admin-dashboard').then(
            (module) => module.AdminDashboard,
          ),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
