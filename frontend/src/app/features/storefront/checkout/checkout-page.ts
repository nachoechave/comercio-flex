import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize, switchMap } from 'rxjs';

import { CsrfService } from '../../../core/auth/csrf.service';
import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { QuantityFormatPipe } from '../../../shared/pipes/quantity-format.pipe';
import { CartService } from '../cart/cart.service';
import { CheckoutProNavigationService } from '../payment/checkout-pro-navigation.service';
import { PaymentApiService } from '../payment/payment-api.service';
import { PaymentRecoveryService } from '../payment/payment-recovery.service';
import { StorefrontApiService } from '../storefront-api.service';
import { storefrontErrorMessage } from '../storefront-errors';
import { StorefrontContextService } from '../storefront-context.service';
import { StorefrontMoneyPipe } from '../storefront-money.pipe';
import { CreateGuestOrder } from '../storefront.models';

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
  protected readonly form = this.formBuilder.nonNullable.group({
    customerName: ['', [Validators.required, Validators.maxLength(160)]],
    customerPhone: ['', [Validators.required, Validators.maxLength(40)]],
    customerEmail: ['', [Validators.email, Validators.maxLength(254)]],
    notes: ['', [Validators.maxLength(1000)]],
  });

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

    const value = this.form.getRawValue();
    const body: CreateGuestOrder = {
      customerName: value.customerName.trim(),
      customerPhone: value.customerPhone.trim(),
      ...(value.customerEmail.trim() ? { customerEmail: value.customerEmail.trim() } : {}),
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
          return this.paymentApi.startCheckout(
            storeSlug,
            response.order.id,
            response.lookupToken,
            this.paymentIdempotencyKey!,
          );
        }),
        finalize(() => {
          this.submitting.set(false);
          this.form.enable();
        }),
      )
      .subscribe({
        next: (payment) => {
          const order = createdOrder;
          if (!order) return;
          void this.router
            .navigate(['/tiendas', storeSlug, 'pedidos', order.id], {
              queryParams: { token: order.lookupToken },
              replaceUrl: true,
            })
            .then(() => {
              try {
                this.paymentNavigation.navigate(payment.checkoutUrl);
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

  private paymentsNotEnabled(error: unknown): boolean {
    return (
      error instanceof HttpErrorResponse &&
      typeof error.error === 'object' &&
      error.error?.code === 'PAYMENTS_NOT_ENABLED'
    );
  }
}
