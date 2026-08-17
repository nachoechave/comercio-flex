import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-super-admin-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './super-admin-layout.html',
  styleUrl: './super-admin-layout.scss',
})
export class SuperAdminLayout {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly user = this.auth.user;
  readonly loggingOut = signal(false);
  readonly logoutError = signal<string | null>(null);

  logout(): void {
    this.loggingOut.set(true);
    this.logoutError.set(null);
    this.auth
      .logout()
      .pipe(finalize(() => this.loggingOut.set(false)))
      .subscribe({
        next: () => void this.router.navigate(['/admin/login']),
        error: () =>
          this.logoutError.set('No pudimos cerrar la sesión. Intentá nuevamente.'),
      });
  }
}
