import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-storefront-layout',
  imports: [RouterLink, RouterOutlet],
  template: `
    <header class="site-header">
      <a class="brand" routerLink="/">Comercio Flex</a>
      <a routerLink="/admin">Administración</a>
    </header>
    <main id="main-content">
      <router-outlet />
    </main>
  `,
  styles: `
    .site-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      max-width: 70rem;
      margin: 0 auto;
      padding: 1rem;
    }

    .brand {
      color: var(--color-text);
      font-size: 1.125rem;
      font-weight: 750;
      text-decoration: none;
    }

    @media (max-width: 32rem) {
      .site-header {
        align-items: flex-start;
        flex-direction: column;
        gap: 0.75rem;
      }
    }
  `,
})
export class StorefrontLayout {}
