import { Routes } from '@angular/router';

export const PAYMENT_CONNECTION_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./payment-connection-page/payment-connection-page').then(
        (module) => module.PaymentConnectionPage,
      ),
  },
];
