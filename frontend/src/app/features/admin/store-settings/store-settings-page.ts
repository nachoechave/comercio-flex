import { Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { exhaustMap, finalize } from 'rxjs';

import { CsrfService } from '../../../core/auth/csrf.service';
import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { StoreSettingsApiService } from './store-settings-api.service';

@Component({ selector: 'app-store-settings-page', imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './store-settings-page.html', styleUrl: './store-settings-page.scss' })
export class StoreSettingsPage {
  private readonly api = inject(StoreSettingsApiService);
  private readonly csrf = inject(CsrfService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), { initialValue: '' });
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly noticeMessage = signal<string | null>(null);
  readonly form = this.formBuilder.nonNullable.group({
    storeName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(160)]],
    contactPhone: ['', [Validators.maxLength(40), Validators.pattern(/^$|^[+0-9][0-9 ()\-]{6,39}$/)]],
    contactEmail: ['', [Validators.email, Validators.maxLength(254)]],
    pickupAddress: ['', [Validators.required, Validators.minLength(5), Validators.maxLength(240)]],
    pickupInstructions: ['', Validators.maxLength(500)],
    bankTransferEnabled: [false],
    bankTransferDiscountPercentage: [
      0,
      [Validators.min(0), Validators.max(50)],
    ],
    bankName: ['', Validators.maxLength(120)],
    bankAccountHolder: ['', Validators.maxLength(160)],
    bankAlias: ['', Validators.maxLength(120)],
    bankCbuCvu: ['', [Validators.maxLength(40), Validators.pattern(/^$|^[0-9]{6,40}$/)]],
  });

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      if (!slug) { this.loading.set(false); return; }
      const subscription = this.api.get(slug).subscribe({
        next: (settings) => {
          this.form.setValue({ storeName: settings.storeName,
            contactPhone: settings.contactPhone ?? '',
            contactEmail: settings.contactEmail ?? '',
            pickupAddress: settings.pickupAddress ?? '',
            pickupInstructions: settings.pickupInstructions ?? '',
            bankTransferEnabled: settings.bankTransferEnabled ?? false,
            bankTransferDiscountPercentage: settings.bankTransferDiscountPercentage ?? 0,
            bankName: settings.bankName ?? '', bankAccountHolder: settings.bankAccountHolder ?? '',
            bankAlias: settings.bankAlias ?? '', bankCbuCvu: settings.bankCbuCvu ?? '' });
          this.loading.set(false);
        },
        error: () => { this.errorMessage.set('No pudimos cargar la configuración.'); this.loading.set(false); },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  save(): void {
    this.form.markAllAsTouched();
    const value = this.form.getRawValue();
    const invalidBankTransfer = value.bankTransferEnabled &&
      (!value.bankAccountHolder.trim() || (!value.bankAlias.trim() && !value.bankCbuCvu.trim()));
    if (this.form.invalid || (!value.contactPhone.trim() && !value.contactEmail.trim()) ||
        invalidBankTransfer || this.saving()) {
      this.errorMessage.set('Revisá los campos. Necesitamos al menos un teléfono o correo.');
      return;
    }
    const slug = this.storeSlug();
    if (!slug) { this.errorMessage.set('No pudimos identificar el comercio.'); return; }
    this.saving.set(true); this.errorMessage.set(null); this.noticeMessage.set(null);
    this.csrf.ensureToken().pipe(
      exhaustMap(() => this.api.update(slug, { ...value,
        storeName: value.storeName.trim(), contactPhone: value.contactPhone.trim(),
        contactEmail: value.contactEmail.trim(), pickupAddress: value.pickupAddress.trim(),
        pickupInstructions: value.pickupInstructions.trim(), bankName: value.bankName.trim(),
        bankAccountHolder: value.bankAccountHolder.trim(), bankAlias: value.bankAlias.trim(),
        bankCbuCvu: value.bankCbuCvu.trim() })),
      finalize(() => this.saving.set(false)),
    ).subscribe({ next: () => this.noticeMessage.set('La tienda quedó actualizada.'),
      error: () => this.errorMessage.set('No pudimos guardar los cambios.') });
  }
}
