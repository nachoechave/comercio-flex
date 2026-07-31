import { DatePipe } from '@angular/common';
import { Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize, map, Subscription, switchMap } from 'rxjs';

import { CsrfService } from '../../../core/auth/csrf.service';
import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { CheckoutProNavigationService } from '../payment/checkout-pro-navigation.service';
import { PaymentApiService } from '../payment/payment-api.service';
import { paymentErrorMessage } from '../payment/payment-errors';
import { PaymentRecoveryService } from '../payment/payment-recovery.service';
import { StorefrontApiService } from '../storefront-api.service';
import { storefrontErrorMessage } from '../storefront-errors';
import { StorefrontMoneyPipe } from '../storefront-money.pipe';
import { GuestOrder, GuestOrderItem, GuestOrderStatus } from '../storefront.models';

@Component({
  selector: 'app-order-confirmation-page',
  imports: [DatePipe, RouterLink, StorefrontMoneyPipe],
  templateUrl: './order-confirmation-page.html',
  styleUrl: './order-confirmation-page.scss',
})
export class OrderConfirmationPage {
  private readonly api = inject(StorefrontApiService);
  private readonly csrf = inject(CsrfService);
  private readonly paymentApi = inject(PaymentApiService);
  private readonly paymentNavigation = inject(CheckoutProNavigationService);
  private readonly paymentRecovery = inject(PaymentRecoveryService);
  private readonly route = inject(ActivatedRoute);
  protected readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: '',
  });
  protected readonly orderId = toSignal(inheritedRouteParam(this.route, 'orderId'), {
    initialValue: '',
  });
  protected readonly token = toSignal(
    this.route.queryParamMap.pipe(map((params) => params.get('token') ?? '')),
    { initialValue: this.route.snapshot.queryParamMap.get('token') ?? '' },
  );
  protected readonly paymentResult = toSignal(
    this.route.queryParamMap.pipe(map((params) => params.get('payment') ?? '')),
    { initialValue: this.route.snapshot.queryParamMap.get('payment') ?? '' },
  );
  protected readonly order = signal<GuestOrder | null>(null);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly paymentStarting = signal(false);
  protected readonly paymentErrorMessage = signal<string | null>(null);
  protected readonly paymentsUnavailable = signal(false);

  constructor() {
    effect((onCleanup) => {
      const storeSlug = this.storeSlug();
      const orderId = this.orderId();
      const token = this.token();
      this.order.set(null);
      this.errorMessage.set(null);
      this.paymentErrorMessage.set(null);
      this.paymentsUnavailable.set(this.paymentResult() === 'not-enabled');
      if (!storeSlug || !orderId || !token) {
        this.loading.set(false);
        this.errorMessage.set('El enlace del pedido está incompleto.');
        return;
      }
      this.loading.set(true);
      const recovery = this.paymentRecovery.find(storeSlug, orderId);
      if (!recovery || recovery.lookupToken !== token) {
        this.paymentRecovery.remember(storeSlug, orderId, token);
      }
      const subscription: Subscription = this.api.getOrder(storeSlug, orderId, token).subscribe({
        next: (order) => {
          this.order.set(order);
          this.loading.set(false);
          if (this.paymentResult() === 'failed') {
            this.paymentErrorMessage.set(
              'Tu pedido quedó guardado, pero no pudimos abrir Mercado Pago. Podés intentarlo nuevamente.',
            );
          } else if (this.paymentResult() === 'not-enabled') {
            this.paymentErrorMessage.set(
              'El pago en línea no está habilitado para esta tienda. El comercio podrá coordinar el pedido con vos.',
            );
          }
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.errorMessage.set(storefrontErrorMessage(error, 'No pudimos recuperar este pedido.'));
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  protected startPayment(): void {
    if (this.paymentStarting()) return;
    const storeSlug = this.storeSlug();
    const orderId = this.orderId();
    const token = this.token();
    if (!storeSlug || !orderId || !token) {
      this.paymentErrorMessage.set('El enlace privado del pedido está incompleto.');
      return;
    }
    const recovery =
      this.paymentRecovery.find(storeSlug, orderId) ??
      this.paymentRecovery.remember(storeSlug, orderId, token);
    this.paymentStarting.set(true);
    this.paymentErrorMessage.set(null);
    this.csrf
      .ensureToken()
      .pipe(
        switchMap(() =>
          this.paymentApi.startCheckout(
            storeSlug,
            orderId,
            recovery.lookupToken,
            recovery.idempotencyKey,
          ),
        ),
        finalize(() => this.paymentStarting.set(false)),
      )
      .subscribe({
        next: (payment) => {
          try {
            this.paymentNavigation.navigate(payment.checkoutUrl);
          } catch {
            this.paymentErrorMessage.set(
              'No pudimos abrir el destino seguro de Mercado Pago. Tu pedido sigue guardado.',
            );
          }
        },
        error: (error: unknown) => {
          if (
            typeof error === 'object' &&
            error !== null &&
            'error' in error &&
            typeof error.error === 'object' &&
            error.error !== null &&
            'code' in error.error &&
            error.error.code === 'PAYMENTS_NOT_ENABLED'
          ) {
            this.paymentsUnavailable.set(true);
          }
          this.paymentErrorMessage.set(
            paymentErrorMessage(error, 'No pudimos iniciar el pago. Tu pedido sigue guardado.'),
          );
        },
      });
  }

  protected statusLabel(status: GuestOrderStatus): string {
    const labels: Record<GuestOrderStatus, string> = {
      PENDING_CONFIRMATION: 'Pedido recibido',
      CONFIRMED: 'Pedido confirmado',
      READY_FOR_PICKUP: 'Listo para retirar',
      COMPLETED: 'Pedido completado',
      REJECTED: 'Pedido rechazado',
      CANCELLED: 'Pedido cancelado',
      EXPIRED: 'Reserva vencida',
    };
    return labels[status];
  }

  protected statusMessage(order: GuestOrder): string {
    const messages: Record<GuestOrderStatus, string> = {
      PENDING_CONFIRMATION: this.paymentsUnavailable()
        ? `Guardamos el pedido de ${order.customerName}. El comercio podrá contactarte por ${order.contactHint}.`
        : `Guardamos el pedido de ${order.customerName}. Completá el pago para confirmarlo.`,
      CONFIRMED: 'El pedido está confirmado. El comercio preparará tu compra.',
      READY_FOR_PICKUP: 'Tu compra está preparada y lista para retirar en el comercio.',
      COMPLETED: 'El pedido fue entregado y quedó completado.',
      REJECTED: 'El comercio no pudo aceptar este pedido.',
      CANCELLED: 'Este pedido fue cancelado.',
      EXPIRED: 'La reserva de stock venció. Armá un nuevo pedido para continuar.',
    };
    return messages[order.status];
  }

  protected optionLabel(item: GuestOrderItem): string {
    return [item.size && `Talle ${item.size}`, item.color].filter(Boolean).join(' · ');
  }
}
