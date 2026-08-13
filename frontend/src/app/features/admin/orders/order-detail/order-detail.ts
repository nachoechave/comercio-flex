import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { inheritedRouteParam } from '../../../../core/routing/inherited-route-param';
import { QuantityFormatPipe } from '../../../../shared/pipes/quantity-format.pipe';
import { StorefrontMoneyPipe } from '../../../storefront/storefront-money.pipe';
import { OrderApiService } from '../order-api.service';
import {
  AdminOrderDetail as OrderDetailModel,
  ORDER_ACTION_LABELS,
  ORDER_STATUS_LABELS,
  ORDER_TRANSITIONS,
  OrderStatus,
} from '../order.models';

@Component({
  selector: 'app-order-detail',
  imports: [DatePipe, QuantityFormatPipe, ReactiveFormsModule, RouterLink, StorefrontMoneyPipe],
  templateUrl: './order-detail.html',
  styleUrl: './order-detail.scss',
})
export class OrderDetail {
  private readonly api = inject(OrderApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(FormBuilder);
  private transitionFingerprint: string | null = null;
  private transitionKey: string | null = null;

  readonly labels = ORDER_STATUS_LABELS;
  readonly actionLabels = ORDER_ACTION_LABELS;
  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: '',
  });
  readonly orderId = toSignal(inheritedRouteParam(this.route, 'orderId'), {
    initialValue: '',
  });
  readonly order = signal<OrderDetailModel | null>(null);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly actions = computed(() => ORDER_TRANSITIONS[this.order()?.status ?? 'EXPIRED'] ?? []);
  readonly form = this.formBuilder.nonNullable.group({
    note: ['', Validators.maxLength(500)],
  });

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      const id = this.orderId();
      if (!slug || !id) return;
      this.order.set(null);
      this.loading.set(true);
      this.errorMessage.set(null);
      const subscription = this.api.get(slug, id).subscribe({
        next: (order) => {
          this.order.set(order);
          this.loading.set(false);
        },
        error: () => {
          this.errorMessage.set('No pudimos cargar el pedido.');
          this.loading.set(false);
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  transition(targetStatus: OrderStatus): void {
    if (this.submitting() || this.form.invalid) return;
    if (
      (targetStatus === 'CANCELLED' || targetStatus === 'REJECTED') &&
      !globalThis.confirm(
        `¿Confirmás que querés marcar el pedido como ${this.labels[targetStatus]}?`,
      )
    ) {
      return;
    }
    const note = this.form.controls.note.value.trim();
    const fingerprint = JSON.stringify({ targetStatus, note });
    if (fingerprint !== this.transitionFingerprint) {
      this.transitionFingerprint = fingerprint;
      this.transitionKey = globalThis.crypto.randomUUID();
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.api
      .transition(
        this.storeSlug() ?? '',
        this.orderId() ?? '',
        this.transitionKey!,
        targetStatus,
        note,
      )
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (order) => {
          this.order.set(order);
          this.form.reset();
          this.transitionFingerprint = null;
          this.transitionKey = null;
        },
        error: (error: unknown) => {
          if (!(error instanceof HttpErrorResponse) || (error.status > 0 && error.status < 500)) {
            this.transitionFingerprint = null;
            this.transitionKey = null;
          }
          this.errorMessage.set(
            'No pudimos cambiar el estado. Recargá el pedido para verificar su situación actual.',
          );
        },
      });
  }

  optionLabel(size: string | null, color: string | null): string {
    return [size, color].filter(Boolean).join(' · ');
  }
}
