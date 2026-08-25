import { Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize, Observable } from 'rxjs';

import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { CommerceDatePipe } from '../../../shared/pipes/commerce-date.pipe';
import { StorefrontMoneyPipe } from '../../storefront/storefront-money.pipe';
import { BankTransferApiService } from './bank-transfer-api.service';
import { AdminBankTransferPayment } from './bank-transfer.models';

@Component({
  selector: 'app-bank-transfer-detail',
  imports: [CommerceDatePipe, ReactiveFormsModule, RouterLink, StorefrontMoneyPipe],
  templateUrl: './bank-transfer-detail.html',
  styleUrl: './bank-transfer-detail.scss',
})
export class BankTransferDetail {
  private readonly api = inject(BankTransferApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(FormBuilder);
  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), { initialValue: '' });
  readonly paymentId = toSignal(inheritedRouteParam(this.route, 'paymentId'), { initialValue: '' });
  readonly payment = signal<AdminBankTransferPayment | null>(null);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly form = this.formBuilder.nonNullable.group({
    reason: ['', [Validators.required, Validators.maxLength(500)]],
  });

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      const id = this.paymentId();
      if (!slug || !id) return;
      const subscription = this.api.get(slug, id).subscribe({
        next: (payment) => { this.payment.set(payment); this.loading.set(false); },
        error: () => { this.errorMessage.set('No pudimos cargar la transferencia.'); this.loading.set(false); },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  receiptUrl(): string { return this.api.receiptUrl(this.storeSlug() ?? '', this.paymentId() ?? ''); }

  approve(): void {
    if (this.submitting() || !globalThis.confirm(
      '¿Confirmás el pago? Esta acción confirma la venta y consume el stock.',
    )) return;
    this.submit(this.api.approve(this.storeSlug() ?? '', this.paymentId() ?? ''));
  }

  reject(): void {
    this.form.markAllAsTouched();
    if (this.submitting() || this.form.invalid) return;
    this.submit(this.api.reject(
      this.storeSlug() ?? '', this.paymentId() ?? '', this.form.controls.reason.value.trim(),
    ));
  }

  private submit(request: Observable<AdminBankTransferPayment>): void {
    this.submitting.set(true);
    this.errorMessage.set(null);
    request.pipe(finalize(() => this.submitting.set(false))).subscribe({
      next: (payment) => this.payment.set(payment),
      error: () => this.errorMessage.set(
        'No pudimos completar la revisión. Verificá que la reserva siga vigente.',
      ),
    });
  }
}
