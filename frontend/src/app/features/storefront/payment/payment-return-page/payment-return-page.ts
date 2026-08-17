import { DatePipe } from '@angular/common';
import { Component, ElementRef, OnDestroy, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  EMPTY,
  Subscription,
  catchError,
  exhaustMap,
  finalize,
  take,
  takeWhile,
  tap,
  timer,
} from 'rxjs';

import { CsrfService } from '../../../../core/auth/csrf.service';
import { CheckoutProNavigationService } from '../checkout-pro-navigation.service';
import { PaymentApiService } from '../payment-api.service';
import { paymentErrorMessage } from '../payment-errors';
import { PaymentRecoveryService } from '../payment-recovery.service';
import { PaymentReturnStatus, PublicPaymentStatus } from '../payment.models';

const PENDING_STATUSES = new Set<PublicPaymentStatus>(['CREATED', 'PENDING']);

@Component({
  selector: 'app-payment-return-page',
  imports: [DatePipe, RouterLink],
  templateUrl: './payment-return-page.html',
  styleUrl: './payment-return-page.scss',
})
export class PaymentReturnPage implements OnDestroy {
  private readonly api = inject(PaymentApiService);
  private readonly csrf = inject(CsrfService);
  private readonly navigation = inject(CheckoutProNavigationService);
  private readonly recovery = inject(PaymentRecoveryService);
  private readonly route = inject(ActivatedRoute);
  private readonly title = viewChild<ElementRef<HTMLElement>>('resultTitle');
  private pollingSubscription?: Subscription;
  private lastAnnouncementKey?: string;

  protected readonly storeSlug = this.route.snapshot.paramMap.get('storeSlug') ?? '';
  protected readonly returnToken = this.route.snapshot.paramMap.get('returnToken') ?? '';
  private readonly providerPaymentId = this.validProviderPaymentId(
    this.route.snapshot.queryParamMap.get('payment_id'),
  );
  private readonly shouldInspectReturn =
    !this.providerPaymentId && this.hasProviderReturnContext();
  protected readonly result = signal<PaymentReturnStatus | null>(null);
  protected readonly loading = signal(true);
  protected readonly refreshing = signal(false);
  protected readonly retrying = signal(false);
  protected readonly timedOut = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly announcement = signal('Consultando el estado del pago.');

  constructor() {
    if (!this.storeSlug || !this.returnToken) {
      this.loading.set(false);
      this.errorMessage.set('El enlace de confirmación está incompleto.');
      return;
    }
    this.startPolling();
  }

  ngOnDestroy(): void {
    this.pollingSubscription?.unsubscribe();
  }

  protected refresh(): void {
    if (this.refreshing()) return;
    this.refreshing.set(true);
    this.errorMessage.set(null);
    const request = this.providerPaymentId
      ? this.csrf
          .ensureToken()
          .pipe(
            exhaustMap(() =>
              this.api.reconcileReturn(this.storeSlug, this.returnToken, this.providerPaymentId!),
            ),
          )
      : this.shouldInspectReturn
        ? this.csrf
            .ensureToken()
            .pipe(
              exhaustMap(() => this.api.inspectReturn(this.storeSlug, this.returnToken)),
              catchError(() => this.api.getReturnStatus(this.storeSlug, this.returnToken)),
            )
        : this.api.getReturnStatus(this.storeSlug, this.returnToken);
    request.pipe(finalize(() => this.refreshing.set(false))).subscribe({
      next: (result) => this.receive(result),
      error: (error: unknown) =>
        this.errorMessage.set(
          paymentErrorMessage(error, 'No pudimos actualizar el estado. Intentá nuevamente.'),
        ),
    });
  }

  protected retryPayment(): void {
    const result = this.result();
    if (!result?.canRetry || this.retrying()) return;
    const recovery = this.recovery.find(this.storeSlug, result.orderId);
    if (!recovery) {
      this.errorMessage.set('Abrí el enlace privado del pedido para volver a intentar el pago.');
      return;
    }
    this.retrying.set(true);
    this.errorMessage.set(null);
    this.csrf
      .ensureToken()
      .pipe(
        exhaustMap(() =>
          this.api.startCheckout(
            this.storeSlug,
            result.orderId,
            recovery.lookupToken,
            recovery.idempotencyKey,
          ),
        ),
        finalize(() => this.retrying.set(false)),
      )
      .subscribe({
        next: (checkout) => {
          try {
            this.navigation.navigate(checkout.checkoutUrl);
          } catch {
            this.errorMessage.set(
              'No pudimos abrir el destino seguro de Mercado Pago. Tu pedido sigue guardado.',
            );
          }
        },
        error: (error: unknown) =>
          this.errorMessage.set(
            paymentErrorMessage(error, 'No pudimos reiniciar el pago. Tu pedido sigue guardado.'),
          ),
      });
  }

  protected isPending(status: PublicPaymentStatus): boolean {
    return PENDING_STATUSES.has(status);
  }

  protected canRetryPayment(result: PaymentReturnStatus): boolean {
    return result.canRetry && !this.isAwaitingConfirmation(result);
  }

  protected statusLabel(result: PaymentReturnStatus): string {
    if (result.returnOutcome === 'PAYMENT_NOT_RECORDED') return 'Pago no completado';
    const status = result.paymentStatus;
    return {
      CREATED: 'Pago iniciado',
      PENDING: 'Pago pendiente',
      APPROVED: 'Pago aprobado',
      REJECTED: 'Pago rechazado',
      EXPIRED: 'Pago vencido',
      REQUIRES_REVIEW: 'Pago en revisión',
    }[status];
  }

  protected statusMessage(result: PaymentReturnStatus): string {
    if (result.returnOutcome === 'PAYMENT_NOT_RECORDED') {
      return 'Mercado Pago no registró ningún cobro. Tu pedido sigue reservado temporalmente y podés intentar nuevamente.';
    }
    const status = result.paymentStatus;
    return {
      CREATED: 'Estamos confirmando tu pago. No vuelvas a pagar.',
      PENDING: 'Estamos confirmando tu pago. No vuelvas a pagar.',
      APPROVED: 'Tu pago fue aprobado y el pedido quedó confirmado.',
      REJECTED: 'El pago no pudo completarse.',
      EXPIRED: 'La reserva venció. Volvé a la tienda para iniciar un nuevo pedido.',
      REQUIRES_REVIEW:
        'Recibimos información del pago y el comercio debe revisarla. No vuelvas a pagar.',
    }[status];
  }

  protected privateOrderLink(result: PaymentReturnStatus): unknown[] | null {
    return this.recovery.find(this.storeSlug, result.orderId)
      ? ['/tiendas', this.storeSlug, 'pedidos', result.orderId]
      : null;
  }

  protected privateOrderToken(result: PaymentReturnStatus): string | null {
    return this.recovery.find(this.storeSlug, result.orderId)?.lookupToken ?? null;
  }

  private startPolling(): void {
    this.pollingSubscription = timer(0, 3_000)
      .pipe(
        take(11),
        exhaustMap((iteration) =>
          this.pollRequest(iteration).pipe(
            catchError((error: unknown) => {
              this.loading.set(false);
              this.errorMessage.set(
                paymentErrorMessage(error, 'No pudimos consultar el estado del pago.'),
              );
              return EMPTY;
            }),
          ),
        ),
        tap((result) => this.receive(result)),
        takeWhile((result) => this.isAwaitingConfirmation(result), true),
        finalize(() => {
          const result = this.result();
          this.loading.set(false);
          if (result && this.isAwaitingConfirmation(result)) this.timedOut.set(true);
        }),
      )
      .subscribe();
  }

  private receive(result: PaymentReturnStatus): void {
    const firstResult = this.result() === null;
    this.result.set(result);
    this.loading.set(false);
    this.errorMessage.set(null);
    const announcementKey = `${result.paymentStatus}:${result.returnOutcome ?? ''}`;
    if (announcementKey !== this.lastAnnouncementKey) {
      this.lastAnnouncementKey = announcementKey;
      this.announcement.set(
        `${this.statusLabel(result)}. ${this.statusMessage(result)}`,
      );
    }
    if (firstResult) queueMicrotask(() => this.title()?.nativeElement.focus());
  }

  private validProviderPaymentId(value: string | null): string | null {
    return value && /^[0-9]{1,20}$/.test(value) ? value : null;
  }

  private hasProviderReturnContext(): boolean {
    return ['payment', 'status', 'collection_status', 'preference_id', 'merchant_order_id'].some(
      (name) => this.route.snapshot.queryParamMap.has(name),
    );
  }

  private pollRequest(iteration: number) {
    if (iteration === 0 && this.providerPaymentId) {
      return this.csrf
        .ensureToken()
        .pipe(
          exhaustMap(() =>
            this.api.reconcileReturn(
              this.storeSlug,
              this.returnToken,
              this.providerPaymentId!,
            ),
          ),
        );
    }
    if (iteration === 0 && this.shouldInspectReturn) {
      return this.csrf
        .ensureToken()
        .pipe(
          exhaustMap(() => this.api.inspectReturn(this.storeSlug, this.returnToken)),
          catchError(() => this.api.getReturnStatus(this.storeSlug, this.returnToken)),
        );
    }
    return this.api.getReturnStatus(this.storeSlug, this.returnToken);
  }

  private isAwaitingConfirmation(result: PaymentReturnStatus): boolean {
    return this.isPending(result.paymentStatus) && result.returnOutcome !== 'PAYMENT_NOT_RECORDED';
  }
}
