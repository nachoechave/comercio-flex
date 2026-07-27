import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./layouts/storefront-layout/storefront-layout').then(
        (module) => module.StorefrontLayout,
      ),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/storefront/home/storefront-home').then(
            (module) => module.StorefrontHome,
          ),
      },
    ],
  },
  {
    path: 'admin',
    loadComponent: () =>
      import('./layouts/admin-layout/admin-layout').then((module) => module.AdminLayout),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/admin/dashboard/admin-dashboard').then(
            (module) => module.AdminDashboard,
          ),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
