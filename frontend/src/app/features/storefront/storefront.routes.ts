import { Routes } from '@angular/router';

export const STOREFRONT_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./catalog/catalog-page').then((module) => module.CatalogPage),
  },
  {
    path: 'productos/:productSlug',
    loadComponent: () =>
      import('./product-detail/public-product-detail').then(
        (module) => module.PublicProductDetail,
      ),
  },
];
