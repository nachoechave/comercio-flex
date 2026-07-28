import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink, RouterOutlet } from '@angular/router';
import { finalize, map } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-admin-layout',
  imports: [RouterLink, RouterOutlet],
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
      max-width: 70rem;
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

    button {
      min-height: 2.5rem;
      padding: 0.5rem 0.75rem;
      border: 1px solid var(--color-border);
      border-radius: 0.5rem;
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
      max-width: 70rem;
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
