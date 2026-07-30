import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize, switchMap } from 'rxjs';

import { CsrfService } from '../../../core/auth/csrf.service';
import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { CartService } from '../cart/cart.service';
import { StorefrontApiService } from '../storefront-api.service';
import { storefrontErrorMessage } from '../storefront-errors';
import { StorefrontContextService } from '../storefront-context.service';
import { StorefrontMoneyPipe } from '../storefront-money.pipe';
import { CreateGuestOrder } from '../storefront.models';

@Component({
  selector: 'app-checkout-page',
  imports: [ReactiveFormsModule, RouterLink, StorefrontMoneyPipe],
  templateUrl: './checkout-page.html',
  styleUrl: './checkout-page.scss',
})
export class CheckoutPage {
  private readonly api = inject(StorefrontApiService);
  private readonly cart = inject(CartService);
  private readonly csrf = inject(CsrfService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly context = inject(StorefrontContextService);
  private intentFingerprint: string | null = null;
  private idempotencyKey: string | null = null;

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
    }

    const storeSlug = this.storeSlug();
    if (!storeSlug) {
      this.errorMessage.set('No pudimos identificar el comercio.');
      return;
    }
    this.submitting.set(true);
    this.form.disable();
    this.csrf
      .ensureToken()
      .pipe(
        switchMap(() => this.api.createOrder(storeSlug, this.idempotencyKey!, body)),
        finalize(() => {
          this.submitting.set(false);
          this.form.enable();
        }),
      )
      .subscribe({
        next: (response) => {
          this.cart.clear(storeSlug);
          void this.router.navigate(['/tiendas', storeSlug, 'pedidos', response.order.id], {
            queryParams: { token: response.lookupToken },
            replaceUrl: true,
          });
        },
        error: (error: unknown) => {
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
}
