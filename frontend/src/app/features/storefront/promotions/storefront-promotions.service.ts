import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';

import { QuantityPromotion } from '../storefront.models';

@Injectable({ providedIn: 'root' })
export class StorefrontPromotionsService {
  private readonly http = inject(HttpClient);
  private readonly byStore = signal<Record<string, QuantityPromotion[]>>({});
  private readonly loaded = new Set<string>();
  private readonly loading = new Set<string>();

  ensure(storeSlug: string): void {
    if (!storeSlug || this.loaded.has(storeSlug) || this.loading.has(storeSlug)) return;
    this.loading.add(storeSlug);
    this.http
      .get<QuantityPromotion[]>(
        `/api/v1/stores/${encodeURIComponent(storeSlug)}/catalog/promotions`,
      )
      .pipe(finalize(() => this.loading.delete(storeSlug)))
      .subscribe({
        next: (promotions) => {
          // BigDecimal is serialized by Spring/Jackson as a JSON number. Product prices use
          // strings elsewhere in the storefront, so normalize promotion prices at this boundary
          // before passing them to StorefrontMoneyPipe or the cart calculator.
          const normalized = promotions.map((promotion) => ({
            ...promotion,
            bundlePrice: String(promotion.bundlePrice),
          }));
          this.byStore.update((current) => ({ ...current, [storeSlug]: normalized }));
          this.loaded.add(storeSlug);
        },
        error: () => {
          this.byStore.update((current) => ({ ...current, [storeSlug]: [] }));
        },
      });
  }

  promotion(storeSlug: string, productId: string): QuantityPromotion | null {
    return (this.byStore()[storeSlug] ?? []).find((promo) => promo.productIds.includes(productId)) ?? null;
  }

  promotions(storeSlug: string): QuantityPromotion[] {
    return this.byStore()[storeSlug] ?? [];
  }

  calculateDiscount(
    storeSlug: string,
    lines: readonly { productId: string; unitPrice: string; quantity: number }[],
  ): number {
    let bestDiscount = 0;

    for (const promotion of this.promotions(storeSlug)) {
      if (promotion.bundleQuantity < 2) continue;

      const eligibleProducts = new Set(promotion.productIds);
      const prices: number[] = [];
      for (const line of lines) {
        if (!eligibleProducts.has(line.productId)) continue;
        const unitPrice = Number(line.unitPrice);
        if (!Number.isFinite(unitPrice) || unitPrice <= 0) continue;
        for (let index = 0; index < line.quantity; index++) prices.push(unitPrice);
      }

      prices.sort((left, right) => right - left);
      const bundleCount = Math.floor(prices.length / promotion.bundleQuantity);
      const promotedUnits = bundleCount * promotion.bundleQuantity;
      if (promotedUnits === 0) continue;

      const regular = prices.slice(0, promotedUnits).reduce((sum, value) => sum + value, 0);
      const promotional = Number(promotion.bundlePrice) * bundleCount;
      bestDiscount = Math.max(bestDiscount, regular - promotional);
    }

    return Math.round(Math.max(0, bestDiscount) * 100) / 100;
  }}
