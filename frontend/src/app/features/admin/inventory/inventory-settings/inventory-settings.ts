import { Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { exhaustMap, finalize } from 'rxjs';

import { CsrfService } from '../../../../core/auth/csrf.service';
import { inheritedRouteParam } from '../../../../core/routing/inherited-route-param';
import { DashboardApiService } from '../../dashboard/dashboard-api.service';

@Component({
  selector: 'app-inventory-settings',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './inventory-settings.html',
  styleUrl: './inventory-settings.scss',
})
export class InventorySettings {
  private readonly api = inject(DashboardApiService);
  private readonly csrf = inject(CsrfService);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(FormBuilder);

  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), { initialValue: '' });
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly noticeMessage = signal<string | null>(null);
  readonly form = this.formBuilder.nonNullable.group({
    threshold: ['', [Validators.required, Validators.pattern(/^\d{1,12}(?:\.\d{1,3})?$/)]],
  });

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      if (!slug) {
        this.loading.set(false);
        this.errorMessage.set('No pudimos identificar el comercio solicitado.');
        return;
      }
      const subscription = this.api.get(slug).subscribe({
        next: (summary) => {
          this.form.controls.threshold.setValue(summary.lowStockThreshold);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.errorMessage.set('No pudimos cargar la configuración de inventario.');
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const slug = this.storeSlug();
    if (!slug) return;
    this.saving.set(true);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);
    this.csrf.ensureToken().pipe(
      exhaustMap(() => this.api.updateThreshold(slug, this.form.controls.threshold.value)),
      finalize(() => this.saving.set(false)),
    ).subscribe({
      next: (summary) => {
        this.form.controls.threshold.setValue(summary.lowStockThreshold);
        this.noticeMessage.set('Actualizamos el umbral de stock bajo.');
      },
      error: () => this.errorMessage.set('No pudimos actualizar el umbral. Intentá nuevamente.'),
    });
  }
}
