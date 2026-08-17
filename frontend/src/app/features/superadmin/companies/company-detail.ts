import { DatePipe } from '@angular/common';
import { Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { SuperAdminApiService } from '../super-admin-api.service';
import {
  COMPANY_STATUS_LABELS,
  CompanyDetail,
  CompanyStatus,
} from '../super-admin.models';

type StatusAction = 'activate' | 'suspend';

@Component({
  selector: 'app-company-detail',
  imports: [DatePipe, RouterLink],
  templateUrl: './company-detail.html',
  styleUrl: './company-detail.scss',
})
export class CompanyDetailPage {
  private readonly api = inject(SuperAdminApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly companyId = toSignal(inheritedRouteParam(this.route, 'companyId'), {
    initialValue: null,
  });

  readonly company = signal<CompanyDetail | null>(null);
  readonly loading = signal(true);
  readonly changingStatus = signal(false);
  readonly pendingAction = signal<StatusAction | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly noticeMessage = signal<string | null>(null);

  constructor() {
    effect((onCleanup) => {
      const companyId = this.companyId();
      this.company.set(null);
      this.errorMessage.set(null);
      this.noticeMessage.set(null);
      if (!companyId) {
        this.loading.set(false);
        this.errorMessage.set('No pudimos identificar la empresa solicitada.');
        return;
      }
      this.loading.set(true);
      const subscription = this.api.company(companyId).subscribe({
        next: (company) => {
          this.company.set(company);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.errorMessage.set('No pudimos cargar la empresa solicitada.');
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  statusLabel(status: CompanyStatus): string {
    return COMPANY_STATUS_LABELS[status];
  }

  ask(action: StatusAction): void {
    this.pendingAction.set(action);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);
  }

  cancel(): void {
    this.pendingAction.set(null);
  }

  confirm(): void {
    const action = this.pendingAction();
    const company = this.company();
    if (!action || !company || this.changingStatus()) return;

    this.changingStatus.set(true);
    this.errorMessage.set(null);
    const operation =
      action === 'activate'
        ? this.api.activate(company.id)
        : this.api.suspend(company.id);
    operation.pipe(finalize(() => this.changingStatus.set(false))).subscribe({
      next: (updated) => {
        this.company.set(updated);
        this.pendingAction.set(null);
        this.noticeMessage.set(
          action === 'activate'
            ? 'La empresa quedó activa.'
            : 'La empresa quedó suspendida.',
        );
      },
      error: () => {
        this.pendingAction.set(null);
        this.errorMessage.set(
          'No pudimos cambiar el estado. Actualizá la página e intentá nuevamente.',
        );
      },
    });
  }

  retryProvisioning(): void {
    const company = this.company();
    if (!company || this.changingStatus()) return;

    this.changingStatus.set(true);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);
    this.api
      .retryProvisioning(company.id)
      .pipe(finalize(() => this.changingStatus.set(false)))
      .subscribe({
        next: (updated) => {
          this.company.set(updated);
          this.noticeMessage.set('El tenant quedó aprovisionado correctamente.');
        },
        error: () => {
          this.errorMessage.set(
            'El reintento no pudo completarse. El tenant permanece aislado y puede volver a intentarse.',
          );
        },
      });
  }
}
