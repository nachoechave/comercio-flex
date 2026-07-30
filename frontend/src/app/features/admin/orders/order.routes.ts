import { Routes } from '@angular/router';

export const ORDER_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./order-list/order-list').then((module) => module.OrderList),
  },
  {
    path: ':orderId',
    loadComponent: () => import('./order-detail/order-detail').then((module) => module.OrderDetail),
  },
];
