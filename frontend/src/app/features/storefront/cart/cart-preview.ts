import { Component, HostListener, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { variantOptionsLabel } from '../../../shared/variant-options';
import { StorefrontContextService } from '../storefront-context.service';
import { StorefrontMoneyPipe } from '../storefront-money.pipe';
import { CartLine } from './cart.models';
import { CartPreviewService } from './cart-preview.service';
import { CartService } from './cart.service';

@Component({
  selector: 'app-cart-preview',
  imports: [RouterLink, StorefrontMoneyPipe],
  templateUrl: './cart-preview.html',
  styleUrl: './cart-preview.scss',
})
export class CartPreview {
  private readonly cart = inject(CartService);
  private readonly preview = inject(CartPreviewService);
  protected readonly context = inject(StorefrontContextService);

  readonly storeSlug = input.required<string>();
  protected readonly isOpen = computed(() => this.preview.storeSlug() === this.storeSlug());
  protected readonly items = computed(() => this.cart.items(this.storeSlug()));
  protected readonly visibleItems = computed(() => this.items().slice(-3).reverse());
  protected readonly remainingItems = computed(() =>
    Math.max(0, this.items().length - this.visibleItems().length),
  );
  protected readonly totalUnits = computed(() => this.cart.totalUnits(this.storeSlug()));
  protected readonly subtotal = computed(() => this.cart.availableSubtotal(this.storeSlug()));

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
