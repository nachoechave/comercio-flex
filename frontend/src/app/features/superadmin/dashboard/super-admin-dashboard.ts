import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SuperAdminApiService } from '../super-admin-api.service';
import { SuperAdminDashboardSummary } from '../super-admin.models';

@Component({
  selector: 'app-super-admin-dashboard',
  imports: [RouterLink],
  templateUrl: './super-admin-dashboard.html',
  styleUrl: './super-admin-dashboard.scss',
})
export class SuperAdminDashboard {
  private readonly api = inject(SuperAdminApiService);

  readonly summary = signal<SuperAdminDashboardSummary | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api.dashboard().subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('No pudimos cargar el estado global de la plataforma.');
      },
    });
  }
}
