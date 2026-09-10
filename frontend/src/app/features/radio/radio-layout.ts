import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { RadioContext } from './radio-context';

@Component({
  selector: 'app-radio-layout',
  imports: [RouterLink, RouterOutlet],
  providers: [RadioContext],
  styleUrl: './radio-account.scss',
  template: `
    <div class="radio-shell">
      <nav aria-label="Navegación de la radio">
        <a [routerLink]="context.link()">Inicio</a>
        <a [routerLink]="context.link('socios')">Socios</a>
        @if (auth.isAuthenticated()) {
          <a [routerLink]="context.link('mi-cuenta')">Mi cuenta</a>
          <a [routerLink]="context.link('mi-cuenta', 'plan')">Mi plan</a>
          <a [routerLink]="context.link('mi-cuenta', 'cuotas')">Cuotas</a>
          <a [routerLink]="context.link('mi-cuenta', 'perfil')">Mi perfil</a>
          <button type="button" (click)="logout()" [disabled]="busy()">Cerrar sesión</button>
        } @else {
          <a [routerLink]="context.link('ingresar')">Iniciar sesión</a>
          <a [routerLink]="context.link('registro')">Crear cuenta</a>
        }
      </nav>
      @if (error()) { <p role="alert">{{ error() }}</p> }
      <main><router-outlet /></main>
    </div>`,
})
export class RadioLayout {
  readonly context = inject(RadioContext);
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly busy = signal(false);
  readonly error = signal('');
  constructor() { this.auth.loadSession().subscribe(); }
  logout() {
    this.busy.set(true); this.error.set('');
    this.auth.logout().pipe(finalize(() => this.busy.set(false))).subscribe({
      next: () => void this.router.navigate(this.context.link('ingresar')),
      error: () => this.error.set('No pudimos cerrar la sesión. Intentá nuevamente.'),
    });
  }
}
