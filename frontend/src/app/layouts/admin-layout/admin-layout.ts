import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-admin-layout',
  imports: [RouterLink, RouterOutlet],
  template: `
    <header class="admin-header">
      <a routerLink="/">← Volver a la tienda</a>
      <strong>Administración</strong>
    </header>
    <main id="main-content">
      <router-outlet />
    </main>
  `,
  styles: `
    .admin-header {
      display: flex;
      align-items: center;
      gap: 1rem;
      max-width: 70rem;
      margin: 0 auto;
      padding: 1rem;
      border-bottom: 1px solid var(--color-border);
    }
  `,
})
export class AdminLayout {}
