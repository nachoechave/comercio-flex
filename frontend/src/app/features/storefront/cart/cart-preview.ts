import { Component, HostListener, computed, effect, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { variantOptionsLabel } from '../../../shared/variant-options';
import { StorefrontContextService } from '../storefront-context.service';
import { StorefrontMoneyPipe } from '../storefront-money.pipe';
import { StorefrontPromotionsService } from '../promotions/storefront-promotions.service';
import { CartLine } from './cart.models';
import { CartPreviewService } from './cart-preview.service';
import { CartService } from './cart.service';
import { StorefrontRoutingService } from '../storefront-routing.service';

@Component({
  selector: 'app-cart-preview',
  imports: [RouterLink, StorefrontMoneyPipe],
  templateUrl: './cart-preview.html',
  styleUrl: './cart-preview.scss',
})
export class CartPreview {
  private readonly cart = inject(CartService);
  private readonly preview = inject(CartPreviewService);
  private readonly promotions = inject(StorefrontPromotionsService);
  protected readonly context = inject(StorefrontContextService);
  protected readonly storefrontRouting = inject(StorefrontRoutingService);

  readonly storeSlug = input.required<string>();
  protected readonly isOpen = computed(() => this.preview.storeSlug() === this.storeSlug());
  protected readonly items = computed(() => this.cart.items(this.storeSlug()));
  protected readonly visibleItems = computed(() => this.items().slice(-3).reverse());
  protected readonly remainingItems = computed(() =>
    Math.max(0, this.items().length - this.visibleItems().length),
  );
  protected readonly totalUnits = computed(() => this.cart.totalUnits(this.storeSlug()));
  protected readonly subtotal = computed(() => this.cart.availableSubtotal(this.storeSlug()));
  protected readonly promotionDiscount = computed(() =>
    this.promotions.calculateDiscount(
      this.storeSlug(),
      this.items()
        .filter((line) => line.status === 'AVAILABLE')
        .map((line) => ({
          productId: line.productId,
          unitPrice: line.unitPrice,
          quantity: line.quantity,
        })),
    ),
  );
  protected readonly promotionalSubtotal = computed(() =>
    Math.max(0, Number(this.subtotal()) - this.promotionDiscount()).toFixed(2),
  );

  constructor() {
    effect(() => this.promotions.ensure(this.storeSlug()));
  }

  protected close(): void {
    this.preview.close();
  }

  protected optionLabel(line: CartLine): string {
    return variantOptionsLabel(line.options, line.size, line.color);
  }

  @HostListener('document:keydown.escape')
  protected closeOnEscape(): void {
    if (this.isOpen()) this.close();
  }
}
