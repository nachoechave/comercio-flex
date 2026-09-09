import { Routes } from '@angular/router';

export const RADIO_ROUTES: Routes = [
  { path: '', pathMatch: 'full', loadComponent: () => import('./radio-placeholder').then(m => m.RadioPlaceholder) },
  { path: '**', redirectTo: '' },
];
