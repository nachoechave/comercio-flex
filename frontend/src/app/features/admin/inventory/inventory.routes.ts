import { Routes } from '@angular/router';

export const INVENTORY_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./inventory-list/inventory-list').then((module) => module.InventoryList),
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
