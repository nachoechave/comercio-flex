import { Routes } from '@angular/router';
import { radioAccountGuard } from './radio-account.guard';

export const RADIO_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./radio-layout').then(m => m.RadioLayout),
    children: [
      { path: '', pathMatch: 'full', loadComponent: () => import('./radio-placeholder').then(m => m.RadioPlaceholder) },
      ...(['registro', 'ingresar', 'olvide-contrasena', 'nueva-contrasena'] as const).map((path, index) => ({
        path, data: { mode: ['register', 'login', 'forgot', 'reset'][index] },
        loadComponent: () => import('./radio-auth-page').then(m => m.RadioAuthPage),
      })),
      { path: 'mi-cuenta', canActivate: [radioAccountGuard], canActivateChild: [radioAccountGuard], children: [
        { path: '', pathMatch: 'full', loadComponent: () => import('./radio-private-page').then(m => m.RadioPrivatePage) },
        { path: 'perfil', data: { profile: true }, canActivate: [radioAccountGuard], loadComponent: () => import('./radio-private-page').then(m => m.RadioPrivatePage) },
      ] },
      { path: '**', redirectTo: '' },
    ],
  },
];
