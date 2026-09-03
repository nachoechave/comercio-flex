import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize, Subscription } from 'rxjs';

import { StorefrontRoutingService } from '../storefront-routing.service';
import { variantOptionsLabel } from '../../../shared/variant-options';
import { StorefrontApiService } from '../storefront-api.service';
import { StorefrontContextService } from '../storefront-context.service';
import { StorefrontMoneyPipe } from '../storefront-money.pipe';
import { CartLine } from './cart.models';
import { CartService } from './cart.service';

@Component({
  selector: 'app-cart-page',
  imports: [RouterLink, StorefrontMoneyPipe],
  templateUrl: './cart-page.html',
  styleUrl: './cart-page.scss',
})
export class CartPage {
  private readonly api = inject(StorefrontApiService);
  protected readonly cart = inject(CartService);
  protected readonly context = inject(StorefrontContextService);
  private readonly route = inject(ActivatedRoute);
  protected readonly storefrontRouting = inject(StorefrontRoutingService);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly retryVersion = signal(0);

  protected readonly storeSlug = toSignal(
    this.storefrontRouting.storeSlug(this.route),
    {
      initialValue: this.route.snapshot.paramMap.get('storeSlug') ?? '',
    },
  );
  protected readonly isStreetwear = computed(
    () => this.context.settings()?.branding?.template === 'FASHION',
  );
  protected readonly isMinimal = computed(
    () => this.context.settings()?.branding?.template === 'CATALOG',
  );
  protected readonly items = computed(() => this.cart.items(this.storeSlug() ?? ''));
  protected readonly subtotal = computed(() => this.cart.availableSubtotal(this.storeSlug() ?? ''));
  protected readonly canCheckout = computed(
    () =>
      !this.validating() &&
      this.items().length > 0 &&
      this.items().every((line) => line.status === 'AVAILABLE'),
  );
  protected readonly validating = signal(false);
  protected readonly validationWarning = signal<string | null>(null);
  protected readonly actionMessage = signal('');
  protected readonly confirmingClear = signal(false);

  constructor() {
    effect((onCleanup) => {
      const storeSlug = this.storeSlug();
      this.retryVersion();
      if (!storeSlug) return;

      this.cart.activate(storeSlug);
      const productSlugs = [
        ...new Set(untracked(() => this.cart.items(storeSlug).map((line) => line.productSlug))),
      ];
      this.validationWarning.set(null);
      this.confirmingClear.set(false);
      if (productSlugs.length === 0) {
        this.validating.set(false);
        return;
      }

      this.validating.set(true);
      let pending = productSlugs.length;
      let failed = 0;
      const subscriptions = new Subscription();
      const finish = () => {
        pending -= 1;
        if (pending === 0) {
          this.validating.set(false);
          if (failed > 0) {
            this.validationWarning.set(
              'No pudimos confirmar todos los productos. Podés reintentar sin perder el carrito.',
            );
          }
        }
      };

      for (const productSlug of productSlugs) {
        subscriptions.add(
          this.api
            .getProduct(storeSlug, productSlug)
            .pipe(finalize(finish))
            .subscribe({
              next: (product) => this.cart.reconcileProduct(storeSlug, product),
              error: (error: unknown) => {
                if (error instanceof HttpErrorResponse && error.status === 404) {
                  this.cart.markProductUnavailable(storeSlug, productSlug);
                } else {
                  failed += 1;
                  this.cart.markProductUnknown(storeSlug, productSlug);
                }
              },
            }),
        );
      }
      onCleanup(() => subscriptions.unsubscribe());
    });

    effect(() => {
      const settings = this.context.settings();
      const storeSlug = this.storeSlug();
      if (settings?.slug === storeSlug) {
        this.title.setTitle(`Carrito | ${settings.storeName}`);
        this.meta.updateTag({
          name: 'description',
          content: `Revisá los productos elegidos en ${settings.storeName}.`,
        });
      } else {
        this.title.setTitle('Carrito | Comercio Flex');
        this.meta.updateTag({
          name: 'description',
          content: 'Revisá los productos de tu carrito en Comercio Flex.',
        });
      }
    });
  }

  protected optionLabel(line: CartLine): string {
    return variantOptionsLabel(line.options, line.size, line.color) || 'Opción estándar';
  }

  protected lineTotal(line: CartLine): string {
    const [integer, fraction = ''] = line.unitPrice.split('.');
    const cents = BigInt(integer) * 100n + BigInt(fraction.padEnd(2, '0'));
    const total = cents * BigInt(line.quantity);
    return `${total / 100n}.${(total % 100n).toString().padStart(2, '0')}`;
  }

  protected updateQuantity(line: CartLine, event: Event): void {
    const input = event.target as HTMLInputElement;
    const quantity = input.valueAsNumber;
    if (!this.cart.setQuantity(this.storeSlug() ?? '', line.variantId, quantity)) {
      input.value = String(line.quantity);
      this.actionMessage.set('La cantidad debe ser un número entero entre 1 y 99.');
      return;
    }
    this.actionMessage.set(`Actualizamos la cantidad de ${line.productName}.`);
  }

  protected remove(line: CartLine): void {
    this.cart.remove(this.storeSlug() ?? '', line.variantId);
    this.actionMessage.set(`Quitamos ${line.productName} del carrito.`);
  }

  protected requestClear(): void {
    this.confirmingClear.set(true);
  }

  protected cancelClear(): void {
    this.confirmingClear.set(false);
  }

  protected clear(): void {
    this.cart.clear(this.storeSlug() ?? '');
    this.confirmingClear.set(false);
    this.actionMessage.set('Vaciamos el carrito.');
  }

  protected retryValidation(): void {
    this.retryVersion.update((version) => version + 1);
  }
}
