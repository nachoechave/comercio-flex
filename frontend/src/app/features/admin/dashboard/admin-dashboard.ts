import { Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { exhaustMap, finalize } from 'rxjs';

import { CsrfService } from '../../../core/auth/csrf.service';
import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { DashboardApiService } from './dashboard-api.service';
import { DashboardSummary } from './dashboard.models';

@Component({
  selector: 'app-admin-dashboard',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './admin-dashboard.html',
  styleUrl: './admin-dashboard.scss',
})
export class AdminDashboard {
  private readonly api = inject(DashboardApiService);
  private readonly csrf = inject(CsrfService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly reloadVersion = signal(0);

  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: '',
  });
  readonly summary = signal<DashboardSummary | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly noticeMessage = signal<string | null>(null);
  readonly thresholdForm = this.formBuilder.nonNullable.group({
    threshold: [
      '5.000',
      [
        Validators.required,
        Validators.pattern(/^\d{1,12}(?:\.\d{1,3})?$/),
      ],
    ],
  });

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      this.reloadVersion();
      this.summary.set(null);
      this.errorMessage.set(null);
      this.noticeMessage.set(null);
      this.loading.set(true);
      if (!slug) {
        this.loading.set(false);
        this.errorMessage.set('No pudimos identificar el comercio solicitado.');
        return;
      }
      const subscription = this.api.get(slug).subscribe({
        next: (summary) => {
          this.receive(summary);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.errorMessage.set('No pudimos cargar el resumen operativo.');
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  reload(): void {
    this.reloadVersion.update((value) => value + 1);
  }

  saveThreshold(): void {
    if (this.thresholdForm.invalid || this.saving()) {
      this.thresholdForm.markAllAsTouched();
      return;
    }
    const slug = this.storeSlug();
    if (!slug) return;
    this.saving.set(true);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);
    this.csrf
      .ensureToken()
      .pipe(
        exhaustMap(() =>
          this.api.updateThreshold(slug, this.thresholdForm.controls.threshold.value),
        ),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: (summary) => {
          this.receive(summary);
          this.noticeMessage.set('Actualizamos el umbral de stock bajo.');
        },
        error: () =>
          this.errorMessage.set('No pudimos actualizar el umbral. Intentá nuevamente.'),
      });
  }

  formatMoney(value: string, currencyCode: string): string {
    return new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency: currencyCode,
      minimumFractionDigits: 2,
    }).format(Number(value));
  }

  private receive(summary: DashboardSummary): void {
    this.summary.set(summary);
    this.thresholdForm.controls.threshold.setValue(summary.lowStockThreshold, {
      emitEvent: false,
    });
  }
}
