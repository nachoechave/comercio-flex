import { Routes } from '@angular/router';

import { allowedRolesGuard } from '../../../core/auth/auth.guards';

const PRODUCT_MANAGERS = ['OWNER', 'ADMIN'] as const;

export const PRODUCT_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./product-list/product-list').then((module) => module.ProductList),
  },
  {
    path: 'nuevo',
    canActivate: [allowedRolesGuard(PRODUCT_MANAGERS)],
    loadComponent: () =>
      import('./product-form/product-form').then((module) => module.ProductForm),
  },
  {
    path: ':productId/editar',
    canActivate: [allowedRolesGuard(PRODUCT_MANAGERS)],
    loadComponent: () =>
      import('./product-form/product-form').then((module) => module.ProductForm),
  },
  {
    path: ':productId',
    loadComponent: () =>
      import('./product-detail/product-detail').then((module) => module.ProductDetail),
  },
];
