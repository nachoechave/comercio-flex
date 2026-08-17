import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { SuperAdminApiService } from '../super-admin-api.service';

@Component({
  selector: 'app-company-create',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './company-create.html',
  styleUrl: './company-create.scss',
})
export class CompanyCreate {
  private readonly api = inject(SuperAdminApiService);
  private readonly router = inject(Router);

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(160)],
    }),
    slug: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.maxLength(100),
        Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/),
      ],
    }),
    industry: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(100)],
    }),
    administratorName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(160)],
    }),
    administratorEmail: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(254)],
    }),
    administratorPhone: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(40)],
    }),
    domain: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.maxLength(253),
        Validators.pattern(
          /^$|(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,63}$/,
        ),
      ],
    }),
    initialPassword: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(12), Validators.maxLength(128)],
    }),
    status: new FormControl<'ACTIVE' | 'INACTIVE'>('ACTIVE', { nonNullable: true }),
  });

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.submitting()) return;

    this.submitting.set(true);
    this.errorMessage.set(null);
    const value = this.form.getRawValue();
    this.api
      .createCompany({
        ...value,
        administratorPhone: value.administratorPhone.trim() || null,
        domain: value.domain.trim() || null,
      })
      .subscribe({
        next: (company) => {
          void this.router.navigate(['/superadmin/empresas', company.id], {
            state: { notice: 'La empresa y su tenant quedaron operativos.' },
          });
        },
        error: (error: HttpErrorResponse) => {
          this.submitting.set(false);
          this.errorMessage.set(
            typeof error.error?.detail === 'string'
              ? error.error.detail
              : 'No pudimos crear la empresa. Revisá los datos e intentá nuevamente.',
          );
        },
      });
  }
}
