import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize, map, switchMap } from 'rxjs';

import { CsrfService } from '../../../core/auth/csrf.service';
import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { QuantityFormatPipe } from '../../../shared/pipes/quantity-format.pipe';
import { CartService } from '../cart/cart.service';
import { CheckoutProNavigationService } from '../payment/checkout-pro-navigation.service';
import { PaymentApiService } from '../payment/payment-api.service';
import { PaymentMethods } from '../payment/payment.models';
import { PaymentRecoveryService } from '../payment/payment-recovery.service';
import { StorefrontApiService } from '../storefront-api.service';
import { storefrontErrorMessage } from '../storefront-errors';
import { StorefrontContextService } from '../storefront-context.service';
import { StorefrontMoneyPipe } from '../storefront-money.pipe';
import { CreateGuestOrder } from '../storefront.models';

type CheckoutPaymentMethod = 'MERCADO_PAGO' | 'BANK_TRANSFER';

@Component({
  selector: 'app-checkout-page',
  imports: [QuantityFormatPipe, ReactiveFormsModule, RouterLink, StorefrontMoneyPipe],
  templateUrl: './checkout-page.html',
  styleUrl: './checkout-page.scss',
})
export class CheckoutPage {
  private readonly api = inject(StorefrontApiService);
  private readonly cart = inject(CartService);
  private readonly csrf = inject(CsrfService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly paymentApi = inject(PaymentApiService);
  private readonly paymentNavigation = inject(CheckoutProNavigationService);
  private readonly paymentRecovery = inject(PaymentRecoveryService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly context = inject(StorefrontContextService);
  private intentFingerprint: string | null = null;
  private idempotencyKey: string | null = null;
  private paymentIdempotencyKey: string | null = null;

  protected readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: '',
  });
  protected readonly items = computed(() => this.cart.items(this.storeSlug() ?? ''));
  protected readonly subtotal = computed(() => this.cart.availableSubtotal(this.storeSlug() ?? ''));
  protected readonly ready = computed(
    () => this.items().length > 0 && this.items().every((item) => item.status === 'AVAILABLE'),
  );
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly uncertainResult = signal(false);
  protected readonly paymentMethods = signal<PaymentMethods | null>(null);
  protected readonly paymentMethodsLoading = signal(false);
  protected readonly paymentMethodsErrorMessage = signal<string | null>(null);
  protected readonly selectedPaymentMethod = signal<CheckoutPaymentMethod | null>(null);
  protected readonly submitLabel = computed(() => {
    if (this.submitting()) {
      return this.selectedPaymentMethod() === 'BANK_TRANSFER'
        ? 'Confirmando pedido…'
        : 'Preparando pago…';
    }
    if (this.selectedPaymentMethod() === 'BANK_TRANSFER') {
      return 'Confirmar pedido y pagar por transferencia';
    }
    if (this.selectedPaymentMethod() === 'MERCADO_PAGO') {
      return 'Continuar a Mercado Pago';
    }
    return 'Elegí un medio de pago';
  });
  protected readonly form = this.formBuilder.nonNullable.group({
    customerName: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(160)]],
    customerPhone: ['', [Validators.required, Validators.maxLength(40)]],
    customerEmail: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    notes: ['', [Validators.maxLength(1000)]],
  });

  constructor() {
    effect(() => {
      const storeSlug = this.storeSlug();
      if (storeSlug) this.loadPaymentMethods(storeSlug);
    });
  }

  protected selectPaymentMethod(method: CheckoutPaymentMethod): void {
    const available = this.paymentMethods();
    if (
      (method === 'MERCADO_PAGO' && available?.mercadoPago) ||
      (method === 'BANK_TRANSFER' && available?.bankTransfer)
    ) {
      this.selectedPaymentMethod.set(method);
      this.errorMessage.set(null);
    }
  }

  protected retryPaymentMethods(): void {
    const storeSlug = this.storeSlug();
    if (!storeSlug || this.paymentMethodsLoading()) return;
    this.loadPaymentMethods(storeSlug);
  }

  submit(): void {
    if (this.submitting()) return;
    this.errorMessage.set(null);
    this.uncertainResult.set(false);
    this.form.markAllAsTouched();
    if (this.form.invalid || !this.ready()) {
      this.errorMessage.set(
        this.form.invalid
          ? 'Revisá los datos de contacto marcados.'
          : 'Volvé al carrito para confirmar la disponibilidad de los productos.',
      );
      return;
    }

    const selectedPaymentMethod = this.selectedPaymentMethod();
    if (!selectedPaymentMethod || !this.isPaymentMethodEnabled(selectedPaymentMethod)) {
      this.errorMessage.set(
        this.paymentMethodsErrorMessage() ??
          (this.paymentMethods()
            ? 'Elegí uno de los medios de pago habilitados.'
            : 'No pudimos consultar los medios de pago. Intentá nuevamente.'),
      );
      return;
    }

    const value = this.form.getRawValue();
    const body: CreateGuestOrder = {
      customerName: value.customerName.trim(),
      customerPhone: value.customerPhone.trim(),
      customerEmail: value.customerEmail.trim(),
      ...(value.notes.trim() ? { notes: value.notes.trim() } : {}),
      items: this.items().map((item) => ({
        variantId: item.variantId,
        quantity: String(item.quantity),
      })),
    };
    const fingerprint = JSON.stringify(body);
    if (fingerprint !== this.intentFingerprint) {
      this.intentFingerprint = fingerprint;
      this.idempotencyKey = globalThis.crypto.randomUUID();
      this.paymentIdempotencyKey = globalThis.crypto.randomUUID();
    }

    const storeSlug = this.storeSlug();
    if (!storeSlug) {
      this.errorMessage.set('No pudimos identificar el comercio.');
      return;
    }
    this.submitting.set(true);
    this.form.disable();
    let createdOrder: { id: string; lookupToken: string } | null = null;
    this.csrf
      .ensureToken()
      .pipe(
        switchMap(() => this.api.createOrder(storeSlug, this.idempotencyKey!, body)),
        switchMap((response) => {
          createdOrder = { id: response.order.id, lookupToken: response.lookupToken };
          this.cart.clear(storeSlug);
          this.paymentRecovery.remember(
            storeSlug,
            response.order.id,
            response.lookupToken,
            this.paymentIdempotencyKey!,
          );
          if (selectedPaymentMethod === 'BANK_TRANSFER') {
            return this.paymentApi
              .startBankTransfer(storeSlug, response.order.id, response.lookupToken)
              .pipe(map(() => ({ method: 'BANK_TRANSFER' as const })));
          }
          return this.paymentApi
            .startCheckout(
              storeSlug,
              response.order.id,
              response.lookupToken,
              this.paymentIdempotencyKey!,
            )
            .pipe(map((payment) => ({ method: 'MERCADO_PAGO' as const, payment })));
        }),
        finalize(() => {
          this.submitting.set(false);
          this.form.enable();
        }),
      )
      .subscribe({
        next: (result) => {
          const order = createdOrder;
          if (!order) return;
          if (result.method === 'BANK_TRANSFER') {
            void this.navigateToOrder(storeSlug, order);
            return;
          }
          void this.router
            .navigate(this.orderRoute(storeSlug, order), this.orderNavigationExtras(order))
            .then(() => {
              try {
                this.paymentNavigation.navigate(result.payment.checkoutUrl);
              } catch {
                void this.navigateToRecoverableOrder(storeSlug, order, 'failed');
              }
            });
        },
        error: (error: unknown) => {
          if (createdOrder) {
            const result = this.paymentsNotEnabled(error) ? 'not-enabled' : 'failed';
            void this.navigateToRecoverableOrder(storeSlug, createdOrder, result);
            return;
          }
          const uncertain =
            error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500);
          this.uncertainResult.set(uncertain);
          this.errorMessage.set(
            uncertain
              ? 'No recibimos confirmación. Reintentá: conservaremos la misma solicitud para evitar duplicados.'
              : storefrontErrorMessage(error, 'No pudimos confirmar el pedido.'),
          );
        },
      });
  }

  private navigateToRecoverableOrder(
    storeSlug: string,
    order: { id: string; lookupToken: string },
    payment: 'failed' | 'not-enabled',
  ): Promise<boolean> {
    return this.router.navigate(['/tiendas', storeSlug, 'pedidos', order.id], {
      queryParams: { token: order.lookupToken, payment },
      replaceUrl: true,
    });
  }

  private navigateToOrder(
    storeSlug: string,
    order: { id: string; lookupToken: string },
  ): Promise<boolean> {
    return this.router.navigate(
      this.orderRoute(storeSlug, order),
      this.orderNavigationExtras(order),
    );
  }

  private orderRoute(storeSlug: string, order: { id: string }): string[] {
    return ['/tiendas', storeSlug, 'pedidos', order.id];
  }

  private orderNavigationExtras(order: { lookupToken: string }) {
    return {
      queryParams: { token: order.lookupToken },
      replaceUrl: true,
    };
  }

  private loadPaymentMethods(storeSlug: string): void {
    this.paymentMethods.set(null);
    this.selectedPaymentMethod.set(null);
    this.paymentMethodsLoading.set(true);
    this.paymentMethodsErrorMessage.set(null);
    this.paymentApi
      .getMethods(storeSlug)
      .pipe(finalize(() => this.paymentMethodsLoading.set(false)))
      .subscribe({
        next: (methods) => {
          this.paymentMethods.set(methods);
          if (methods.mercadoPago !== methods.bankTransfer) {
            this.selectedPaymentMethod.set(
              methods.mercadoPago ? 'MERCADO_PAGO' : 'BANK_TRANSFER',
            );
          }
        },
        error: () => {
          this.paymentMethods.set(null);
          this.selectedPaymentMethod.set(null);
          this.paymentMethodsErrorMessage.set(
            'No pudimos consultar los medios de pago. Intentá nuevamente.',
          );
        },
      });
  }

  private isPaymentMethodEnabled(method: CheckoutPaymentMethod): boolean {
    const available = this.paymentMethods();
    return method === 'MERCADO_PAGO'
      ? available?.mercadoPago === true
      : available?.bankTransfer === true;
  }

  private paymentsNotEnabled(error: unknown): boolean {
    return (
      error instanceof HttpErrorResponse &&
      typeof error.error === 'object' &&
      error.error?.code === 'PAYMENTS_NOT_ENABLED'
    );
  }
}
