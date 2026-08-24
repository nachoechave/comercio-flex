import { Routes } from '@angular/router';

export const ORDER_ROUTES: Routes = [
  {
    path: 'transferencias',
    pathMatch: 'full',
    loadComponent: () => import('../bank-transfers/bank-transfer-list').then((module) => module.BankTransferList),
  },
  {
    path: 'transferencias/:paymentId',
    loadComponent: () => import('../bank-transfers/bank-transfer-detail').then((module) => module.BankTransferDetail),
  },
  {
    path: '',
    loadComponent: () => import('./order-list/order-list').then((module) => module.OrderList),
  },
  {
    path: ':orderId',
    loadComponent: () => import('./order-detail/order-detail').then((module) => module.OrderDetail),
  },
];
