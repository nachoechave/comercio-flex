import { Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { exhaustMap, finalize } from 'rxjs';

import { CsrfService } from '../../../core/auth/csrf.service';
import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { StoreSettingsApiService } from './store-settings-api.service';
import { BrandTheme } from './store-settings.models';

@Component({ selector: 'app-store-settings-page', imports: [ReactiveFormsModule],
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
  readonly themes: { value: BrandTheme; label: string }[] = [
    { value: 'VIOLET', label: 'Violeta' }, { value: 'BURGUNDY', label: 'Bordó' },
    { value: 'FOREST', label: 'Bosque' }, { value: 'NAVY', label: 'Azul marino' },
  ];
  readonly form = this.formBuilder.nonNullable.group({
    storeName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(160)]],
    contactPhone: ['', [Validators.maxLength(40), Validators.pattern(/^$|^[+0-9][0-9 ()\-]{6,39}$/)]],
    contactEmail: ['', [Validators.email, Validators.maxLength(254)]],
    pickupAddress: ['', [Validators.required, Validators.minLength(5), Validators.maxLength(240)]],
    pickupInstructions: ['', Validators.maxLength(500)],
    brandTheme: ['VIOLET' as BrandTheme, Validators.required],
  });

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      if (!slug) { this.loading.set(false); return; }
      const subscription = this.api.get(slug).subscribe({
        next: (settings) => {
          this.form.setValue({ storeName: settings.storeName, contactPhone: settings.contactPhone ?? '',
            contactEmail: settings.contactEmail ?? '', pickupAddress: settings.pickupAddress ?? '',
            pickupInstructions: settings.pickupInstructions ?? '', brandTheme: settings.brandTheme ?? 'VIOLET' });
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
    if (this.form.invalid || (!value.contactPhone.trim() && !value.contactEmail.trim()) || this.saving()) {
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
        pickupInstructions: value.pickupInstructions.trim() })),
      finalize(() => this.saving.set(false)),
    ).subscribe({ next: () => this.noticeMessage.set('La tienda quedó actualizada.'),
      error: () => this.errorMessage.set('No pudimos guardar los cambios.') });
  }
}
