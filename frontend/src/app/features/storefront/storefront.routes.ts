import { Routes } from '@angular/router';

export const STOREFRONT_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./catalog/catalog-page').then(
        (module) => module.CatalogPage,
      ),
  },
  {
    path: 'carrito',
    loadComponent: () =>
      import('./cart/cart-page').then(
        (module) => module.CartPage,
      ),
  },
  {
    path: 'checkout',
    loadComponent: () =>
      import('./checkout/checkout-page').then(
        (module) => module.CheckoutPage,
      ),
  },
  {
    path: 'mis-pedidos',
    loadComponent: () =>
      import('./guest-orders/recent-orders-page').then(
        (module) => module.RecentOrdersPage,
      ),
  },
  {
    path: 'pedidos/:orderId',
    loadComponent: () =>
      import('./order-confirmation/order-confirmation-page').then(
        (module) => module.OrderConfirmationPage,
      ),
  },
  {
    path: 'productos/:productSlug',
    loadComponent: () =>
      import('./product-detail/public-product-detail').then(
        (module) => module.PublicProductDetail,
      ),
  },
  {
    path: 'payment-return/:returnToken',
    loadComponent: () =>
      import('./payment/payment-return-page/payment-return-page').then(
        (module) => module.PaymentReturnPage,
      ),
  },
  {
    path: '**',
    redirectTo: '',
  },
];