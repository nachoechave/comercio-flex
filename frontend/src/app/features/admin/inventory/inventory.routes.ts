import { Routes } from '@angular/router';

import { allowedRolesGuard } from '../../../core/auth/auth.guards';

export const INVENTORY_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./inventory-list/inventory-list').then((module) => module.InventoryList),
  },
  {
    path: 'configuracion',
    canActivate: [allowedRolesGuard(['OWNER', 'ADMIN'])],
    loadComponent: () =>
      import('./inventory-settings/inventory-settings').then(
        (module) => module.InventorySettings,
      ),
  },
  {
    path: ':variantId/ajustar',
    loadComponent: () =>
      import('./stock-adjustment-form/stock-adjustment-form').then(
        (module) => module.StockAdjustmentForm,
      ),
  },
  {
    path: ':variantId',
    loadComponent: () =>
      import('./inventory-detail/inventory-detail').then(
        (module) => module.InventoryDetail,
      ),
  },
];
