import { Routes } from '@angular/router';

import { allowedRolesGuard } from '../../../core/auth/auth.guards';

const CATEGORY_MANAGERS = ['OWNER', 'ADMIN'] as const;

export const CATEGORY_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./category-list/category-list').then((module) => module.CategoryList),
  },
  {
    path: 'nueva',
    canActivate: [allowedRolesGuard(CATEGORY_MANAGERS)],
    loadComponent: () =>
      import('./category-form/category-form').then((module) => module.CategoryForm),
  },
  {
    path: ':categoryId/editar',
    canActivate: [allowedRolesGuard(CATEGORY_MANAGERS)],
    loadComponent: () =>
      import('./category-form/category-form').then((module) => module.CategoryForm),
  },
];
