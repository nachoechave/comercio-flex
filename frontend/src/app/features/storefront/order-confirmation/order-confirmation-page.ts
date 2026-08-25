import { DOCUMENT } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, exhaustMap, finalize, map, of, Subscription, switchMap, takeWhile, timer } from 'rxjs';

import { CsrfService } from '../../../core/auth/csrf.service';
import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { QuantityFormatPipe } from '../../../shared/pipes/quantity-format.pipe';
import { CommerceDatePipe } from '../../../shared/pipes/commerce-date.pipe';
import { variantOptionsLabel } from '../../../shared/variant-options';
import { CheckoutProNavigationService } from '../payment/checkout-pro-navigation.service';
import { GuestOrderHistoryService } from '../guest-orders/guest-order-history.service';
import { PaymentApiService } from '../payment/payment-api.service';
import { BankTransferPayment, PaymentMethods } from '../payment/payment.models';
import { paymentErrorMessage } from '../payment/payment-errors';
import { PaymentRecoveryService } from '../payment/payment-recovery.service';
import { StorefrontApiService } from '../storefront-api.service';
import { StorefrontContextService } from '../storefront-context.service';
import { storefrontErrorMessage } from '../storefront-errors';
import { StorefrontMoneyPipe } from '../storefront-money.pipe';
import { GuestOrder, GuestOrderItem, GuestOrderStatus } from '../storefront.models';

@Component({
  selector: 'app-order-confirmation-page',
  imports: [CommerceDatePipe, QuantityFormatPipe, RouterLink, StorefrontMoneyPipe],
  templateUrl: './order-confirmation-page.html',
  styleUrl: './order-confirmation-page.scss',
})
export class OrderConfirmationPage {
  private readonly api = inject(StorefrontApiService);
  private readonly csrf = inject(CsrfService);
  private readonly paymentApi = inject(PaymentApiService);
  private readonly paymentNavigation = inject(CheckoutProNavigationService);
  private readonly paymentRecovery = inject(PaymentRecoveryService);
  private readonly guestOrders = inject(GuestOrderHistoryService);
  private readonly route = inject(ActivatedRoute);
  private readonly document = inject(DOCUMENT);
  protected readonly context = inject(StorefrontContextService);
  protected readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: '',
  });
  protected readonly orderId = toSignal(inheritedRouteParam(this.route, 'orderId'), {
    initialValue: '',
  });
  private readonly queryToken = toSignal(
    this.route.queryParamMap.pipe(map((params) => params.get('token') ?? '')),
    { initialValue: this.route.snapshot.queryParamMap.get('token') ?? '' },
  );
  protected readonly token = computed(() =>
    this.queryToken() ||
    this.guestOrders.find(this.storeSlug() ?? '', this.orderId() ?? '')?.lookupToken ||
    '',
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
  protected readonly paymentMethods = signal<PaymentMethods | null>(null);
  protected readonly paymentMethodsLoading = signal(false);
  protected readonly paymentMethodsErrorMessage = signal<string | null>(null);
  protected readonly bankTransfer = signal<BankTransferPayment | null>(null);
  protected readonly bankTransferStarting = signal(false);
  protected readonly receiptUploading = signal(false);
  protected readonly bankTransferMessage = signal<string | null>(null);

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
      const subscriptions = new Subscription();
      subscriptions.add(this.api.getOrder(storeSlug, orderId, token).subscribe({
        next: (order) => {
          this.applyOrder(storeSlug, order);
          this.loading.set(false);
          this.loadPaymentMethods(storeSlug, orderId, token);
          if (order.status === 'PENDING_CONFIRMATION') {
            subscriptions.add(this.pollOrder(storeSlug, orderId, token));
          }
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
          if (this.isInvalidPrivateOrder(error)) this.guestOrders.remove(storeSlug, orderId);
          this.errorMessage.set(storefrontErrorMessage(error, 'No pudimos recuperar este pedido.'));
        },
      }));
      onCleanup(() => subscriptions.unsubscribe());
    });
  }

  protected startBankTransfer(): void {
    if (this.bankTransferStarting()) return;
    this.bankTransferStarting.set(true);
    this.bankTransferMessage.set(null);
    this.paymentApi
      .startBankTransfer(this.storeSlug() ?? '', this.orderId() ?? '', this.token() ?? '')
      .pipe(finalize(() => this.bankTransferStarting.set(false)))
      .subscribe({
        next: (payment) => this.bankTransfer.set(payment),
        error: (error: unknown) =>
          this.bankTransferMessage.set(
            paymentErrorMessage(error, 'No pudimos iniciar la transferencia.'),
          ),
      });
  }

  protected uploadReceipt(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const payment = this.bankTransfer();
    if (!file || !payment || this.receiptUploading()) return;
    this.receiptUploading.set(true);
    this.bankTransferMessage.set(null);
    this.paymentApi
      .uploadBankTransferReceipt(
        this.storeSlug() ?? '',
        this.orderId() ?? '',
        payment.id,
        this.token() ?? '',
        file,
      )
      .pipe(finalize(() => {
        this.receiptUploading.set(false);
        input.value = '';
      }))
      .subscribe({
        next: (updated) => {
          this.bankTransfer.set(updated);
        },
        error: (error: unknown) =>
          this.bankTransferMessage.set(
            paymentErrorMessage(error, 'No pudimos subir el comprobante.'),
          ),
      });
  }

  protected copy(value: string | null): void {
    if (!value) return;
    void globalThis.navigator.clipboard?.writeText(value);
  }

  protected retryPaymentMethods(): void {
    const storeSlug = this.storeSlug();
    const orderId = this.orderId();
    const token = this.token();
    if (!storeSlug || !orderId || !token || this.paymentMethodsLoading()) return;
    this.loadPaymentMethods(storeSlug, orderId, token);
  }

  private loadPaymentMethods(storeSlug: string, orderId: string, token: string): void {
    this.paymentMethods.set(null);
    this.paymentMethodsLoading.set(true);
    this.paymentMethodsErrorMessage.set(null);
    this.paymentApi.getMethods(storeSlug).subscribe({
      next: (methods) => {
        this.paymentMethodsLoading.set(false);
        this.paymentMethods.set(methods);
        this.paymentApi.getCurrentBankTransfer(storeSlug, orderId, token).subscribe({
          next: (payment) => this.bankTransfer.set(payment),
          error: () => undefined,
        });
      },
      error: () => {
        this.paymentMethodsLoading.set(false);
        this.paymentMethods.set(null);
        this.paymentMethodsErrorMessage.set(
          'No pudimos consultar los medios de pago. Intentá nuevamente.',
        );
      },
    });
  }

  private pollOrder(storeSlug: string, orderId: string, token: string): Subscription {
    return timer(12_000, 12_000).pipe(
      exhaustMap(() => {
        if (this.document.visibilityState === 'hidden') return of(null);
        return this.api.getOrder(storeSlug, orderId, token).pipe(
          switchMap((order) => {
            this.applyOrder(storeSlug, order);
            if (order.status !== 'PENDING_CONFIRMATION') return of({ order, transfer: null });
            return this.paymentApi.getCurrentBankTransfer(storeSlug, orderId, token).pipe(
              map((transfer) => ({ order, transfer })),
              catchError(() => of({ order, transfer: null })),
            );
          }),
          catchError((error: unknown) => {
            if (this.isInvalidPrivateOrder(error)) this.guestOrders.remove(storeSlug, orderId);
            return of(null);
          }),
        );
      }),
      takeWhile((result) => result === null || result.order.status === 'PENDING_CONFIRMATION', true),
    ).subscribe((result) => {
      if (result?.transfer) this.bankTransfer.set(result.transfer);
    });
  }

  private applyOrder(storeSlug: string, order: GuestOrder): void {
    this.order.set(order);
    this.guestOrders.update(storeSlug, order);
  }

  private isInvalidPrivateOrder(error: unknown): boolean {
    return typeof error === 'object' && error !== null && 'status' in error &&
      [401, 403, 404].includes(Number(error.status));
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
    this.paymentsUnavailable.set(false);
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
    return variantOptionsLabel(item.options, item.size, item.color);
  }
}
