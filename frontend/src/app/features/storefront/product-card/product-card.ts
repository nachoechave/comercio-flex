import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PublicProductSummary } from '../storefront.models';
import { StorefrontMoneyPipe } from '../storefront-money.pipe';

@Component({
  selector: 'app-public-product-card',
  imports: [RouterLink, StorefrontMoneyPipe],
  templateUrl: './product-card.html',
  styleUrl: './product-card.scss',
})
export class ProductCard {
  readonly product = input.required<PublicProductSummary>();
  readonly storeSlug = input.required<string>();
  readonly currencyCode = input.required<string>();
  readonly modern = input(false);
  readonly minimal = input(false);
  protected readonly initial = computed(() => this.product().name.trim().slice(0, 1).toUpperCase());
}
