import { Routes } from '@angular/router';

import { allowedRolesGuard } from '../../../core/auth/auth.guards';

export const INVENTORY_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./inventory-list/inventory-list').then((module) => module.InventoryList),
  },
  {
    path: 'operaciones',
    loadComponent: () => import('./inventory-operations-page').then((module) => module.InventoryOperationsPage),
  },
  {
    path: 'sucursales',
    canActivate: [allowedRolesGuard(['OWNER', 'ADMIN'])],
    loadComponent: () => import('./branch-management/branch-management').then((module) => module.BranchManagement),
  },
  {
    path: 'configuracion',
    canActivate: [allowedRolesGuard(['OWNER', 'ADMIN'])],
    loadComponent: () => import('./inventory-settings/inventory-settings').then((module) => module.InventorySettings),
  },
  {
    path: ':variantId/ajustar',
    canActivate: [allowedRolesGuard(['OWNER', 'ADMIN', 'MANAGER'])],
    loadComponent: () => import('./stock-adjustment-form/stock-adjustment-form').then((module) => module.StockAdjustmentForm),
  },
  {
    path: ':variantId',
    loadComponent: () => import('./inventory-detail/inventory-detail').then((module) => module.InventoryDetail),
  },
];
