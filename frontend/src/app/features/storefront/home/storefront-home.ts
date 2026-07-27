import { Component, inject, OnInit, signal } from '@angular/core';

import { HealthService } from '../../../core/health/health.service';
import { StatusPill } from '../../../shared/ui/status-pill/status-pill';

type BackendState = 'loading' | 'up' | 'error';

@Component({
  selector: 'app-storefront-home',
  imports: [StatusPill],
  templateUrl: './storefront-home.html',
  styleUrl: './storefront-home.scss',
})
export class StorefrontHome implements OnInit {
  private readonly healthService = inject(HealthService);

  protected readonly backendState = signal<BackendState>('loading');

  ngOnInit(): void {
    this.healthService.getHealth().subscribe({
      next: (response) => this.backendState.set(response.status === 'UP' ? 'up' : 'error'),
      error: () => this.backendState.set('error'),
    });
  }
}
