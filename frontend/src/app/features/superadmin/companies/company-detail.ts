import { DatePipe } from '@angular/common';
import { Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, finalize, forkJoin, of } from 'rxjs';

import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { SuperAdminApiService } from '../super-admin-api.service';
import {
  COMPANY_STATUS_LABELS,
  CompanyActivityPage,
  CompanyBranding,
  CompanyDetail,
  CompanyInfrastructure,
  CompanyStatus,
  CompanyUser,
  UpdateCompanyRequest,
} from '../super-admin.models';

type StatusAction = 'activate' | 'suspend';
type CompanyTab =
  | 'summary'
  | 'users'
  | 'branding'
  | 'activity'
  | 'configuration'
  | 'infrastructure';

const EMPTY_ACTIVITY: CompanyActivityPage = {
  items: [],
  page: 0,
  size: 20,
  totalItems: 0,
  totalPages: 0,
};

@Component({
  selector: 'app-company-detail',
  imports: [DatePipe, ReactiveFormsModule, RouterLink],
  templateUrl: './company-detail.html',
  styleUrl: './company-detail.scss',
})
export class CompanyDetailPage {
  private readonly api = inject(SuperAdminApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(FormBuilder);
  private readonly companyId = toSignal(inheritedRouteParam(this.route, 'companyId'), {
    initialValue: null,
  });

  readonly company = signal<CompanyDetail | null>(null);
  readonly users = signal<CompanyUser[]>([]);
  readonly branding = signal<CompanyBranding | null>(null);
  readonly activity = signal<CompanyActivityPage>(EMPTY_ACTIVITY);
  readonly infrastructure = signal<CompanyInfrastructure | null>(null);
  readonly selectedTab = signal<CompanyTab>('summary');
  readonly loading = signal(true);
  readonly changingStatus = signal(false);
  readonly saving = signal(false);
  readonly activityLoading = signal(false);
  readonly unavailableSections = signal<CompanyTab[]>([]);
  readonly pendingAction = signal<StatusAction | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly noticeMessage = signal<string | null>(null);
  readonly editForm = this.formBuilder.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(160)]],
    industry: ['', [Validators.required, Validators.maxLength(100)]],
    phone: ['', [Validators.maxLength(40)]],
    domain: [
      '',
      [
        Validators.maxLength(253),
        Validators.pattern(
          /^(?:$|(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,63})$/,
        ),
      ],
    ],
  });

  constructor() {
    effect((onCleanup) => {
      const companyId = this.companyId();
      this.reset();
      if (!companyId) {
        this.loading.set(false);
        this.errorMessage.set('No pudimos identificar la empresa solicitada.');
        return;
      }
      const unavailableSections: CompanyTab[] = [];
      const subscription = forkJoin({
        company: this.api.company(companyId),
        users: this.api.companyUsers(companyId).pipe(
          catchError(() => {
            unavailableSections.push('users');
            return of([] as CompanyUser[]);
          }),
        ),
        branding: this.api.branding(companyId).pipe(
          catchError(() => {
            unavailableSections.push('branding');
            return of(null);
          }),
        ),
        activity: this.api.companyActivity(companyId).pipe(
          catchError(() => {
            unavailableSections.push('activity');
            return of(EMPTY_ACTIVITY);
          }),
        ),
        infrastructure: this.api.companyInfrastructure(companyId).pipe(
          catchError(() => {
            unavailableSections.push('infrastructure');
            return of(null);
          }),
        ),
      }).subscribe({
        next: ({ company, users, branding, activity, infrastructure }) => {
          this.company.set(company);
          this.users.set(users);
          this.branding.set(branding);
          this.activity.set(activity);
          this.infrastructure.set(infrastructure);
          this.unavailableSections.set(unavailableSections);
          this.populateForm(company);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.errorMessage.set('No pudimos cargar la ficha completa de la empresa.');
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  selectTab(tab: CompanyTab): void {
    this.selectedTab.set(tab);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);
  }

  statusLabel(status: CompanyStatus): string {
    return COMPANY_STATUS_LABELS[status];
  }

  roleLabel(role: CompanyUser['role']): string {
    return { OWNER: 'Propietario', ADMIN: 'Administrador', STAFF: 'Personal' }[role];
  }

  userStatusLabel(user: CompanyUser): string {
    if (user.userStatus === 'LOCKED') return 'Usuario bloqueado';
    if (user.userStatus === 'DISABLED') return 'Usuario deshabilitado';
    return user.membershipStatus === 'ACTIVE' ? 'Activo' : 'Membresía inactiva';
  }

  activityLabel(action: string): string {
    return (
      {
        COMPANY_PROVISIONING_STARTED: 'Comenzó el aprovisionamiento',
        COMPANY_PROVISIONING_COMPLETED: 'Finalizó el aprovisionamiento',
        COMPANY_PROVISIONING_FAILED: 'Falló el aprovisionamiento',
        COMPANY_ACTIVATED: 'Activó la empresa',
        COMPANY_SUSPENDED: 'Suspendió la empresa',
        COMPANY_UPDATED: 'Actualizó la configuración de la empresa',
        COMPANY_BRANDING_UPDATED: 'Actualizó la apariencia',
        COMPANY_BRANDING_ASSET_UPDATED: 'Actualizó un recurso de marca',
        COMPANY_BRANDING_ASSET_DELETED: 'Eliminó un recurso de marca',
      }[action] ?? action.replaceAll('_', ' ').toLocaleLowerCase('es')
    );
  }

  infrastructureStatusLabel(status: CompanyInfrastructure['provisioningStatus']): string {
    return {
      PENDING: 'En preparación',
      READY: 'Operativa',
      FAILED: 'Requiere atención',
      EXTERNAL: 'Configuración externa',
    }[status];
  }

  sectionUnavailable(section: CompanyTab): boolean {
    return this.unavailableSections().includes(section);
  }

  unavailableSectionLabels(): string {
    const labels: Partial<Record<CompanyTab, string>> = {
      users: 'Usuarios',
      branding: 'Apariencia',
      activity: 'Actividad',
      infrastructure: 'Infraestructura',
    };
    return this.unavailableSections()
      .map((section) => labels[section] ?? section)
      .join(', ');
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
      action === 'activate' ? this.api.activate(company.id) : this.api.suspend(company.id);
    operation.pipe(finalize(() => this.changingStatus.set(false))).subscribe({
      next: (updated) => {
        this.company.set(updated);
        this.pendingAction.set(null);
        this.noticeMessage.set(
          action === 'activate' ? 'La empresa quedó activa.' : 'La empresa quedó suspendida.',
        );
        this.reloadActivity(0);
      },
      error: () => {
        this.pendingAction.set(null);
        this.errorMessage.set(
          'No pudimos cambiar el estado. Actualizá la página e intentá nuevamente.',
        );
      },
    });
  }

  saveCompany(): void {
    const company = this.company();
    if (!company || this.saving()) return;
    this.editForm.markAllAsTouched();
    if (this.editForm.invalid) {
      this.errorMessage.set('Revisá los campos marcados.');
      return;
    }
    const value = this.editForm.getRawValue();
    const request: UpdateCompanyRequest = {
      name: value.name.trim(),
      industry: value.industry.trim(),
      phone: value.phone.trim() || null,
      domain: value.domain.trim() || null,
    };
    this.saving.set(true);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);
    this.editForm.disable();
    this.api
      .updateCompany(company.id, request)
      .pipe(
        finalize(() => {
          this.saving.set(false);
          this.editForm.enable();
        }),
      )
      .subscribe({
        next: (updated) => {
          this.company.set(updated);
          this.populateForm(updated);
          this.noticeMessage.set('La configuración de la empresa quedó actualizada.');
          this.reloadActivity(0);
        },
        error: () => {
          this.errorMessage.set(
            'No pudimos guardar los cambios. Verificá que el dominio no esté en uso.',
          );
        },
      });
  }

  goToActivityPage(page: number): void {
    if (page < 0 || page >= this.activity().totalPages || page === this.activity().page) return;
    this.reloadActivity(page);
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
          this.reloadInfrastructure(updated.id);
          this.reloadActivity(0);
        },
        error: () => {
          this.errorMessage.set(
            'El reintento no pudo completarse. El tenant permanece aislado y puede volver a intentarse.',
          );
        },
      });
  }

  private reloadActivity(page: number): void {
    const companyId = this.companyId();
    if (!companyId || this.activityLoading()) return;
    this.activityLoading.set(true);
    this.api
      .companyActivity(companyId, page)
      .pipe(finalize(() => this.activityLoading.set(false)))
      .subscribe({ next: (activity) => this.activity.set(activity) });
  }

  private reloadInfrastructure(companyId: string): void {
    this.api.companyInfrastructure(companyId).subscribe({
      next: (infrastructure) => this.infrastructure.set(infrastructure),
    });
  }

  private populateForm(company: CompanyDetail): void {
    this.editForm.setValue({
      name: company.name,
      industry: company.industry ?? '',
      phone: company.phone ?? '',
      domain: company.domain ?? '',
    });
  }

  private reset(): void {
    this.company.set(null);
    this.users.set([]);
    this.branding.set(null);
    this.activity.set(EMPTY_ACTIVITY);
    this.infrastructure.set(null);
    this.unavailableSections.set([]);
    this.selectedTab.set('summary');
    this.loading.set(true);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);
  }
}
