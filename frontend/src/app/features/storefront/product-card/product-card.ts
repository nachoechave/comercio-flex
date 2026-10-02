import { Component, computed, effect, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PublicProductSummary } from '../storefront.models';
import { StorefrontContextService } from '../storefront-context.service';
import { StorefrontMoneyPipe } from '../storefront-money.pipe';
import { StorefrontRoutingService } from '../storefront-routing.service';
import { StorefrontPromotionsService } from '../promotions/storefront-promotions.service';

@Component({
  selector: 'app-public-product-card',
  imports: [RouterLink, StorefrontMoneyPipe],
  templateUrl: './product-card.html',
  styleUrl: './product-card.scss',
})
export class ProductCard {
  protected readonly context = inject(StorefrontContextService);
  protected readonly storefrontRouting = inject(StorefrontRoutingService);
  private readonly promotions = inject(StorefrontPromotionsService);
  readonly product = input.required<PublicProductSummary>();
  readonly storeSlug = input.required<string>();
  readonly currencyCode = input.required<string>();
  readonly modern = input(false);
  readonly minimal = input(false);
  protected readonly initial = computed(() => this.product().name.trim().slice(0, 1).toUpperCase());
  protected readonly promotion = computed(() =>
    this.promotions.promotion(this.storeSlug(), this.product().id),
  );

  constructor() {
    effect(() => this.promotions.ensure(this.storeSlug()));
  }
}
