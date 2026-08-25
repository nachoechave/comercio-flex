import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CommerceDatePipe } from '../../../shared/pipes/commerce-date.pipe';

import { SuperAdminApiService } from '../super-admin-api.service';
import {
  COMPANY_STATUS_LABELS,
  CompanyPage,
  CompanyStatus,
  CompanyStatusFilter,
} from '../super-admin.models';

@Component({
  selector: 'app-company-list',
  imports: [CommerceDatePipe, ReactiveFormsModule, RouterLink],
  templateUrl: './company-list.html',
  styleUrl: './company-list.scss',
})
export class CompanyList {
  private readonly api = inject(SuperAdminApiService);
  private readonly pageSize = 20;

  readonly queryControl = new FormControl('', { nonNullable: true });
  readonly statusControl = new FormControl<CompanyStatusFilter>('ALL', {
    nonNullable: true,
  });
  readonly result = signal<CompanyPage | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly statusLabels = COMPANY_STATUS_LABELS;

  constructor() {
    this.load(0);
  }

  search(): void {
    this.load(0);
  }

  clear(): void {
    this.queryControl.setValue('');
    this.statusControl.setValue('ALL');
    this.load(0);
  }

  previous(): void {
    const page = this.result()?.page ?? 0;
    if (page > 0) this.load(page - 1);
  }

  next(): void {
    const result = this.result();
    if (result && result.page + 1 < result.totalPages) this.load(result.page + 1);
  }

  statusLabel(status: CompanyStatus): string {
    return this.statusLabels[status];
  }

  private load(page: number): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api
      .companies(
        page,
        this.pageSize,
        this.statusControl.value,
        this.queryControl.value,
      )
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.errorMessage.set('No pudimos cargar las empresas.');
        },
      });
  }
}
