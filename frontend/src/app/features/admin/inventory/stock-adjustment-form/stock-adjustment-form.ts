import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize, Subscription } from 'rxjs';

import { routeParam } from '../../../../core/auth/auth.guards';
import { inheritedRouteParam } from '../../../../core/routing/inherited-route-param';
import { InventoryApiService } from '../inventory-api.service';
import { inventoryErrorMessage } from '../inventory-errors';
import {
  AdjustmentDirection,
  AdjustmentReason,
  InventoryItem,
  StockAdjustment,
} from '../inventory.models';

function positiveInteger(control: AbstractControl<string>): ValidationErrors | null {
  return /^[1-9][0-9]{0,11}$/.test(control.value.trim()) ? null : { quantity: true };
}

function toThousandths(value: string): bigint {
  const [whole, fraction = ''] = value.split('.');
  return BigInt(whole) * 1000n + BigInt(fraction.padEnd(3, '0').slice(0, 3));
}

function formatThousandths(value: bigint): string {
  const sign = value < 0n ? '-' : '';
  const absolute = value < 0n ? -value : value;
  return `${sign}${absolute / 1000n}.${(absolute % 1000n).toString().padStart(3, '0')}`;
}

@Component({
  selector: 'app-stock-adjustment-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './stock-adjustment-form.html',
  styleUrl: './stock-adjustment-form.scss',
})
export class StockAdjustmentForm {
  private readonly api = inject(InventoryApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(FormBuilder);
  private mutation?: Subscription;
  private intentFingerprint: string | null = null;
  private idempotencyKey: string | null = null;

  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: routeParam(this.route.snapshot, 'storeSlug') ?? '',
  });
  readonly variantId = toSignal(inheritedRouteParam(this.route, 'variantId'), {
    initialValue: routeParam(this.route.snapshot, 'variantId'),
  });
  readonly inventory = signal<InventoryItem | null>(null);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly uncertainResult = signal(false);
  readonly form = this.formBuilder.nonNullable.group({
    direction: ['INCREASE' as AdjustmentDirection, [Validators.required]],
    quantity: ['', [positiveInteger]],
    reason: ['RECEIPT' as AdjustmentReason, [Validators.required]],
    note: ['', [Validators.maxLength(500)]],
  });
  preview() {
    const inventory = this.inventory();
    const quantity = this.form.controls.quantity.value.trim();
    if (!inventory || !/^[1-9][0-9]{0,11}$/.test(quantity)) return null;
    const current = toThousandths(inventory.quantity);
    const adjustment = BigInt(quantity) * 1000n;
    const result =
      this.form.controls.direction.value === 'INCREASE'
        ? current + adjustment
        : current - adjustment;
    return {
      current: inventory.quantity,
      operator: this.form.controls.direction.value === 'INCREASE' ? '+' : '−',
      quantity: `${quantity}.000`,
      result: formatThousandths(result),
      valid: result >= 0n,
    };
  }

  otherNoteMissing(): boolean {
    return (
      this.form.controls.reason.value === 'OTHER' &&
      this.form.controls.note.touched &&
      !this.form.controls.note.value.trim()
    );
  }

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      const variantId = this.variantId();
      this.mutation?.unsubscribe();
      this.resetForRoute();
      if (!slug || !variantId) {
        this.loading.set(false);
        this.errorMessage.set('No pudimos identificar la variante solicitada.');
        return;
      }
      const subscription = this.api.get(slug, variantId).subscribe({
        next: (inventory) => {
          this.inventory.set(inventory);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.errorMessage.set(inventoryErrorMessage(error, 'No pudimos cargar la existencia.'));
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  submit(): void {
    if (this.submitting()) return;
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.uncertainResult.set(false);
    this.form.markAllAsTouched();
    const value = this.form.getRawValue();
    if (this.form.invalid) {
      this.errorMessage.set('Revisá los campos marcados.');
      return;
    }
    if (value.reason === 'OTHER' && !value.note.trim()) {
      this.errorMessage.set('Explicá el motivo cuando elegís Otro.');
      return;
    }
    const preview = this.preview();
    if (!preview?.valid) {
      this.errorMessage.set('La salida no puede dejar una existencia negativa.');
      return;
    }
    const slug = this.storeSlug();
    const variantId = this.variantId();
    if (!slug || !variantId) return;

    const body: StockAdjustment = {
      direction: value.direction,
      quantity: value.quantity.trim(),
      reason: value.reason,
      ...(value.note.trim() ? { note: value.note.trim() } : {}),
    };
    const fingerprint = JSON.stringify(body);
    if (fingerprint !== this.intentFingerprint) {
      this.intentFingerprint = fingerprint;
      this.idempotencyKey = globalThis.crypto.randomUUID();
    }

    this.submitting.set(true);
    this.form.disable();
    this.mutation = this.api
      .adjust(slug, variantId, this.idempotencyKey!, body)
      .pipe(
        finalize(() => {
          this.submitting.set(false);
          this.form.enable();
        }),
      )
      .subscribe({
        next: (response) => {
          this.inventory.set(response.inventory);
          this.successMessage.set(
            `Ajuste registrado. Nueva existencia: ${response.inventory.quantity}.`,
          );
          this.form.reset({
            direction: 'INCREASE',
            quantity: '',
            reason: 'RECEIPT',
            note: '',
          });
          this.intentFingerprint = null;
          this.idempotencyKey = null;
        },
        error: (error: unknown) => {
          this.uncertainResult.set(
            error instanceof HttpErrorResponse && (error.status === 0 || error.status === 409),
          );
          this.errorMessage.set(
            inventoryErrorMessage(
              error,
              error instanceof HttpErrorResponse && error.status === 0
                ? 'No recibimos confirmación. Verificá el inventario antes de intentar nuevamente.'
                : 'No pudimos registrar el ajuste.',
            ),
          );
        },
      });
  }

  private resetForRoute(): void {
    this.form.enable();
    this.inventory.set(null);
    this.loading.set(true);
    this.submitting.set(false);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.uncertainResult.set(false);
    this.form.reset({ direction: 'INCREASE', quantity: '', reason: 'RECEIPT', note: '' });
    this.intentFingerprint = null;
    this.idempotencyKey = null;
  }
}
