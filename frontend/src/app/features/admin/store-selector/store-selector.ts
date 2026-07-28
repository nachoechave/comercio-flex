import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-store-selector',
  imports: [RouterLink],
  template: `
    <main id="main-content" class="selector-page">
      <section aria-labelledby="selector-title">
        <p class="eyebrow">Panel administrativo</p>
        <h1 id="selector-title">Elegí un comercio</h1>
        <p class="intro">Cada acceso conserva sus propios datos y permisos.</p>

        @if (accessDenied) {
          <p class="notice" role="alert">
            No tenés permiso para administrar el comercio solicitado.
          </p>
        }

        @if (auth.memberships().length > 0) {
          <ul class="store-list">
            @for (membership of auth.memberships(); track membership.storeSlug) {
              <li>
                <a [routerLink]="['/tiendas', membership.storeSlug, 'admin']">
                  <span>{{ membership.storeName }}</span>
                  <small>{{ roleLabel(membership.role) }}</small>
                </a>
              </li>
            }
          </ul>
        } @else {
          <p class="notice" role="status">
            Tu cuenta no tiene comercios activos. Contactá al responsable de la plataforma.
          </p>
        }
      </section>
    </main>
  `,
  styles: `
    .selector-page {
      width: min(100% - 2rem, 52rem);
      margin: 0 auto;
      padding: clamp(3rem, 8vw, 6rem) 0;
    }

    .eyebrow {
      margin: 0;
      color: var(--color-accent);
      font-weight: 750;
    }

    h1 {
      margin: 0.4rem 0;
      font-size: clamp(2rem, 8vw, 3.5rem);
      letter-spacing: -0.04em;
    }

    .intro {
      color: var(--color-muted);
    }

    .store-list {
      display: grid;
      gap: 0.75rem;
      padding: 0;
      list-style: none;
    }

    .store-list a {
      display: flex;
      align-items: center;
      justify-content: space-between;
      min-height: 4rem;
      padding: 1rem;
      border: 1px solid var(--color-border);
      border-radius: 0.75rem;
      color: var(--color-text);
      background: var(--color-surface);
      font-weight: 700;
      text-decoration: none;
    }

    .store-list a:hover {
      border-color: var(--color-accent);
    }

    small {
      color: var(--color-muted);
      font-weight: 500;
    }

    .notice {
      padding: 1rem;
      border: 1px solid var(--color-border);
      border-radius: 0.75rem;
      background: var(--color-surface);
    }
  `,
})
export class StoreSelector {
  readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  readonly accessDenied = this.route.snapshot.queryParamMap.get('denied') === 'true';

  roleLabel(role: string): string {
    const labels: Record<string, string> = {
      OWNER: 'Propietario',
      ADMIN: 'Administrador',
      STAFF: 'Colaborador',
    };
    return labels[role] ?? role;
  }
}
