import { DOCUMENT } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  catchError,
  exhaustMap,
  finalize,
  map,
  of,
  Subscription,
  switchMap,
  takeWhile,
  timer,
} from 'rxjs';

import { CsrfService } from '../../../core/auth/csrf.service';
import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { StorefrontRoutingService } from '../storefront-routing.service';
import { QuantityFormatPipe } from '../../../shared/pipes/quantity-format.pipe';
import { CommerceDatePipe } from '../../../shared/pipes/commerce-date.pipe';
import { variantOptionsLabel } from '../../../shared/variant-options';
import { CheckoutProHandoffService } from '../payment/checkout-pro-handoff.service';
import { CheckoutProNavigationService } from '../payment/checkout-pro-navigation.service';
import { QrCodeService } from '../payment/qr-code.service';
import { GuestOrderHistoryService } from '../guest-orders/guest-order-history.service';
import { PaymentApiService } from '../payment/payment-api.service';
import {
  BankTransferPayment,
  CheckoutProStart,
  PaymentMethods,
  PaymentReturnStatus,
  PublicPaymentStatus,
  QrOrderStart,
} from '../payment/payment.models';
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
  private readonly paymentHandoff = inject(CheckoutProHandoffService);
  private readonly paymentNavigation = inject(CheckoutProNavigationService);
  private readonly paymentQr = inject(QrCodeService);
  private readonly paymentRecovery = inject(PaymentRecoveryService);
  private readonly guestOrders = inject(GuestOrderHistoryService);
  private readonly route = inject(ActivatedRoute);
protected readonly storefrontRouting = inject(StorefrontRoutingService);
  private readonly document = inject(DOCUMENT);
  protected readonly context = inject(StorefrontContextService);
  protected readonly storeSlug = toSignal(
    this.storefrontRouting.storeSlug(this.route),
    {
      initialValue: this.route.snapshot.paramMap.get('storeSlug') ?? '',
    },
  );
   protected readonly productSlug = toSignal(
    inheritedRouteParam(this.route, 'productSlug'),
    {
      initialValue: '',
    },
  );
  protected readonly orderId = toSignal(inheritedRouteParam(this.route, 'orderId'), {
    initialValue: '',
  });
  private readonly queryToken = toSignal(
    this.route.queryParamMap.pipe(map((params) => params.get('token') ?? '')),
    { initialValue: this.route.snapshot.queryParamMap.get('token') ?? '' },
  );
  protected readonly token = computed(
    () =>
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
  protected readonly checkoutPaymentStatus = signal<PublicPaymentStatus | null>(null);
  protected readonly checkoutPro = signal<CheckoutProStart | null>(null);
  protected readonly qrOrder = signal<QrOrderStart | null>(null);
  protected readonly qrDataUrl = signal<string | null>(null);
  protected readonly qrLoading = signal(false);
  protected readonly qrErrorMessage = signal<string | null>(null);
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
      this.checkoutPro.set(null);
      this.qrOrder.set(null);
      this.qrDataUrl.set(null);
      this.qrLoading.set(false);
      this.qrErrorMessage.set(null);
      this.errorMessage.set(null);
      this.paymentErrorMessage.set(null);
      this.checkoutPaymentStatus.set(null);
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
      const pendingCheckout = this.paymentHandoff.find(storeSlug, orderId);
      if (pendingCheckout) this.presentCheckout(pendingCheckout);
      const subscriptions = new Subscription();
      subscriptions.add(
        this.api.getOrder(storeSlug, orderId, token).subscribe({
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
            this.errorMessage.set(
              storefrontErrorMessage(error, 'No pudimos recuperar este pedido.'),
            );
          },
        }),
      );
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
      .pipe(
        finalize(() => {
          this.receiptUploading.set(false);
          input.value = '';
        }),
      )
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
        if (methods.mercadoPagoQr) {
          this.paymentApi.getCurrentQrOrder(storeSlug, orderId, token).subscribe({
            next: (qr) => {
              if (qr) this.presentQr(qr);
            },
            error: () => undefined,
          });
        }
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
    return timer(12_000, 12_000)
      .pipe(
        exhaustMap(() => {
          if (this.document.visibilityState === 'hidden') return of(null);
          const checkoutStatus = this.checkoutPro()
            ? this.paymentApi.reconcilePendingCheckout(storeSlug, orderId, token)
            : of(null);
          return checkoutStatus.pipe(
            catchError(() => of(null)),
            switchMap((payment) =>
              this.api.getOrder(storeSlug, orderId, token).pipe(
                map((order) => ({ order, payment })),
              ),
            ),
            switchMap(({ order, payment }) => {
              this.applyOrder(storeSlug, order);
              this.applyCheckoutPayment(storeSlug, orderId, payment);
              if (order.status !== 'PENDING_CONFIRMATION' || this.isTerminalPayment(payment)) {
                return of({ order, payment, transfer: null, qr: this.qrOrder() });
              }
              if (this.qrOrder()) {
                return this.paymentApi.getCurrentQrOrder(storeSlug, orderId, token).pipe(
                  map((qr) => {
                    if (qr) this.applyQr(qr);
                    return { order, payment, transfer: null, qr };
                  }),
                  catchError(() => of({ order, payment, transfer: null, qr: this.qrOrder() })),
                );
              }
              return this.paymentApi.getCurrentBankTransfer(storeSlug, orderId, token).pipe(
                map((transfer) => ({ order, payment, transfer, qr: null })),
                catchError(() => of({ order, payment, transfer: null, qr: null })),
              );
            }),
            catchError((error: unknown) => {
              if (this.isInvalidPrivateOrder(error)) this.guestOrders.remove(storeSlug, orderId);
              return of(null);
            }),
          );
        }),
        takeWhile(
          (result) =>
            result === null ||
            (result.order.status === 'PENDING_CONFIRMATION' &&
              !this.isTerminalPayment(result.payment) &&
              !this.isTerminalQr(result.qr)),
          true,
        ),
      )
      .subscribe((result) => {
        if (result?.transfer) this.bankTransfer.set(result.transfer);
      });
  }

  private applyCheckoutPayment(
    storeSlug: string,
    orderId: string,
    payment: PaymentReturnStatus | null,
  ): void {
    const status = payment?.paymentStatus ?? null;
    this.checkoutPaymentStatus.set(status);
    if (status === 'REJECTED') {
      this.paymentErrorMessage.set(
        'El pago no pudo completarse. Podés volver a intentar mientras la reserva siga vigente.',
      );
      this.paymentHandoff.forget(storeSlug, orderId);
      this.checkoutPro.set(null);
      this.qrOrder.set(null);
      this.qrDataUrl.set(null);
      this.paymentRecovery.rotateAttempt(storeSlug, orderId);
    } else if (status === 'REQUIRES_REVIEW') {
      this.paymentErrorMessage.set(
        'Recibimos información del pago y el comercio debe revisarla. No vuelvas a pagar.',
      );
      this.checkoutPro.set(null);
      this.qrOrder.set(null);
      this.qrDataUrl.set(null);
    }
  }

  private isTerminalPayment(payment: PaymentReturnStatus | null): boolean {
    return payment !== null && ['APPROVED', 'REJECTED', 'EXPIRED', 'REQUIRES_REVIEW']
      .includes(payment.paymentStatus);
  }

  private applyOrder(storeSlug: string, order: GuestOrder): void {
    this.order.set(order);
    this.guestOrders.update(storeSlug, order);
    if (order.status !== 'PENDING_CONFIRMATION') {
      this.checkoutPro.set(null);
      this.qrOrder.set(null);
      this.qrDataUrl.set(null);
      this.paymentHandoff.forget(storeSlug, order.id);
    }
  }

  private isInvalidPrivateOrder(error: unknown): boolean {
    return (
      typeof error === 'object' &&
      error !== null &&
      'status' in error &&
      [401, 403, 404].includes(Number(error.status))
    );
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
        next: (payment) => this.presentCheckout(payment),
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

  protected openCheckoutPro(): void {
    const checkout = this.checkoutPro();
    if (!checkout) return;
    try {
      this.paymentNavigation.navigate(checkout.checkoutUrl);
    } catch {
      this.paymentErrorMessage.set(
        'No pudimos abrir el destino seguro de Mercado Pago. Tu pedido sigue guardado.',
      );
    }
  }

  protected chooseAnotherPaymentMethod(): void {
    const storeSlug = this.storeSlug();
    const orderId = this.orderId();
    if (storeSlug && orderId) this.paymentHandoff.forget(storeSlug, orderId);
    if (storeSlug && orderId) this.paymentRecovery.rotateAttempt(storeSlug, orderId);
    this.checkoutPro.set(null);
    this.qrOrder.set(null);
    this.qrDataUrl.set(null);
    this.qrLoading.set(false);
    this.qrErrorMessage.set(null);
  }

  private presentCheckout(checkout: CheckoutProStart): void {
    const storeSlug = this.storeSlug();
    const orderId = this.orderId();
    if (storeSlug && orderId) this.paymentHandoff.remember(storeSlug, orderId, checkout);
    this.checkoutPro.set(checkout);
    this.qrOrder.set(null);
    this.qrDataUrl.set(null);
  }

  protected startQrPayment(): void {
    if (this.paymentStarting()) return;
    const storeSlug = this.storeSlug();
    const orderId = this.orderId();
    const token = this.token();
    if (!storeSlug || !orderId || !token) return;
    const recovery =
      this.paymentRecovery.find(storeSlug, orderId) ??
      this.paymentRecovery.remember(storeSlug, orderId, token);
    this.paymentStarting.set(true);
    this.paymentErrorMessage.set(null);
    this.csrf.ensureToken().pipe(
      switchMap(() => this.paymentApi.startQrOrder(
        storeSlug, orderId, recovery.lookupToken, recovery.idempotencyKey,
      )),
      finalize(() => this.paymentStarting.set(false)),
    ).subscribe({
      next: (qr) => this.presentQr(qr),
      error: (error: unknown) => this.paymentErrorMessage.set(
        paymentErrorMessage(error, 'No pudimos generar el QR. Tu pedido sigue guardado.'),
      ),
    });
  }

  protected retryQrPayment(): void {
    const storeSlug = this.storeSlug();
    const orderId = this.orderId();
    if (storeSlug && orderId) this.paymentRecovery.rotateAttempt(storeSlug, orderId);
    this.qrOrder.set(null);
    this.qrDataUrl.set(null);
    this.startQrPayment();
  }

  private presentQr(qr: QrOrderStart): void {
    this.checkoutPro.set(null);
    this.applyQr(qr);
    if (!qr.qrData || qr.status === 'EXPIRED') return;
    this.qrLoading.set(true);
    this.qrErrorMessage.set(null);
    void this.paymentQr.create(qr.qrData).then(
      (dataUrl) => {
        if (this.qrOrder() !== qr) return;
        this.qrDataUrl.set(dataUrl);
        this.qrLoading.set(false);
      },
      () => {
        if (this.qrOrder() !== qr) return;
        this.qrLoading.set(false);
        this.qrErrorMessage.set(
          'No pudimos mostrar el QR. Intentá generarlo nuevamente.',
        );
      },
    );
  }

  private applyQr(qr: QrOrderStart): void {
    this.qrOrder.set(qr);
    if (qr.status === 'APPROVED') {
      this.qrDataUrl.set(null);
    } else if (qr.status === 'EXPIRED' || qr.status === 'REJECTED') {
      this.qrDataUrl.set(null);
      this.paymentRecovery.rotateAttempt(this.storeSlug() ?? '', this.orderId() ?? '');
    }
  }

  private isTerminalQr(qr: QrOrderStart | null): boolean {
    return qr !== null && ['APPROVED', 'EXPIRED', 'REJECTED', 'REQUIRES_REVIEW']
      .includes(qr.status);
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
        : this.checkoutPaymentStatus() === 'PENDING'
          ? `Guardamos el pedido de ${order.customerName}. Estamos verificando tu pago.`
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
