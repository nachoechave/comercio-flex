import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  ActivatedRoute,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { finalize, map } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-admin-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <header class="admin-header">
      <div>
        <a routerLink="/">← Volver a la tienda</a>
        <strong>{{ membership()?.storeName ?? 'Administración' }}</strong>
      </div>
      <div class="session-actions">
        @if (membership(); as currentMembership) {
          <span>{{ currentMembership.role }}</span>
        }
        <button type="button" (click)="logout()" [disabled]="loggingOut()">
          {{ loggingOut() ? 'Saliendo…' : 'Cerrar sesión' }}
        </button>
      </div>
    </header>
    @if (membership(); as currentMembership) {
      <nav class="admin-nav" aria-label="Administración">
        @if (currentMembership.role !== 'STAFF') {
          <a
            [routerLink]="['/tiendas', currentMembership.storeSlug, 'admin']"
            routerLinkActive="active"
            [routerLinkActiveOptions]="{ exact: true }"
          >
            Inicio
          </a>
        }
        <a
          [routerLink]="['/tiendas', currentMembership.storeSlug, 'admin', 'categorias']"
          routerLinkActive="active"
        >
          Categorías
        </a>
        <a
          [routerLink]="['/tiendas', currentMembership.storeSlug, 'admin', 'productos']"
          routerLinkActive="active"
        >
          Productos
        </a>
        <a
          [routerLink]="['/tiendas', currentMembership.storeSlug, 'admin', 'inventario']"
          routerLinkActive="active"
        >
          Inventario
        </a>
        <a
          [routerLink]="['/tiendas', currentMembership.storeSlug, 'admin', 'pedidos']"
          routerLinkActive="active"
        >
          Pedidos
        </a>
        <a
          [routerLink]="[
            '/tiendas', currentMembership.storeSlug, 'admin', 'pedidos', 'transferencias'
          ]"
          routerLinkActive="active"
        >
          Transferencias
        </a>
        @if (currentMembership.role !== 'STAFF') {
          <a
            [routerLink]="[
              '/tiendas', currentMembership.storeSlug, 'admin', 'configuracion', 'comercio'
            ]"
            routerLinkActive="active"
          >
            Comercio
          </a>
        }
        @if (currentMembership.role === 'OWNER') {
          <a
            [routerLink]="[
              '/tiendas',
              currentMembership.storeSlug,
              'admin',
              'configuracion',
              'pagos',
            ]"
            routerLinkActive="active"
          >
            Pagos
          </a>
        }
      </nav>
    }
    @if (logoutError()) {
      <p class="logout-error" role="alert">{{ logoutError() }}</p>
    }
    <main id="main-content">
      <router-outlet />
    </main>
  `,
  styles: `
    .admin-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      max-width: var(--content-platform);
      margin: 0 auto;
      padding: 1rem;
      border-bottom: 1px solid var(--color-border);
    }

    .admin-header > div {
      display: flex;
      align-items: center;
      gap: 1rem;
    }

    .session-actions span {
      color: var(--color-muted);
      font-size: 0.875rem;
    }

    .admin-nav {
      display: flex;
      flex-wrap: wrap;
      gap: 1rem;
      max-width: var(--content-platform);
      margin: 0 auto;
      padding: 0.75rem 1rem;
    }

    .admin-nav a {
      padding: 0.35rem 0;
      color: var(--color-muted);
      text-decoration: none;
    }

    .admin-nav a.active {
      color: var(--color-accent);
      font-weight: 700;
      box-shadow: inset 0 -2px var(--color-accent);
    }

    button {
      min-height: var(--control-height);
      padding: 0.5rem 0.75rem;
      border: 1px solid var(--color-border);
      border-radius: var(--radius-sm);
      color: var(--color-text);
      background: var(--color-surface);
      font: inherit;
      cursor: pointer;
    }

    button:disabled {
      cursor: wait;
      opacity: 0.65;
    }

    .logout-error {
      max-width: var(--content-platform);
      margin: 1rem auto;
      padding: 0 1rem;
      color: #a22323;
    }

    @media (max-width: 42rem) {
      .admin-header,
      .admin-header > div {
        align-items: flex-start;
        flex-direction: column;
      }
    }
  `,
})
export class AdminLayout {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly storeSlug = toSignal(
    this.route.paramMap.pipe(map((params) => params.get('storeSlug') ?? '')),
    { initialValue: this.route.snapshot.paramMap.get('storeSlug') ?? '' },
  );

  readonly membership = computed(() => this.auth.membershipFor(this.storeSlug()));
  readonly loggingOut = signal(false);
  readonly logoutError = signal<string | null>(null);

  logout(): void {
    this.logoutError.set(null);
    this.loggingOut.set(true);
    this.auth
      .logout()
      .pipe(finalize(() => this.loggingOut.set(false)))
      .subscribe({
        next: () => void this.router.navigate(['/admin/login']),
        error: () =>
          this.logoutError.set(
            'No pudimos cerrar la sesión. Revisá tu conexión e intentá nuevamente.',
          ),
      });
  }
}
