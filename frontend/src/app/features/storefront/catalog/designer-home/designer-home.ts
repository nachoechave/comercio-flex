import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { StorefrontContextService } from '../../storefront-context.service';
import { StorefrontMoneyPipe } from '../../storefront-money.pipe';
import { StorefrontRoutingService } from '../../storefront-routing.service';
import { PublicCategory, PublicProductSummary } from '../../storefront.models';
import { DesignerStorefrontTemplate } from '../../storefront-template';

@Component({
  selector: 'app-designer-product-tile',
  imports: [RouterLink, StorefrontMoneyPipe],
  template: `
    <article class="designer-product">
      <a class="designer-product__visual" [routerLink]="storefrontRouting.route(storeSlug(), 'productos', product().slug)" queryParamsHandling="preserve">
        @if (product().image; as image) { <img [src]="image.thumbnailUrl" [alt]="image.altText" loading="lazy" /> }
        @else { <span>{{ initial() }}</span> }
        <i aria-hidden="true">♡</i>
        @if (!product().available) { <b>Sin stock</b> }
      </a>
      <div class="designer-product__copy">
        <small>{{ product().category.name }}</small>
        <strong>{{ product().name }}</strong>
        <div class="designer-product__price">
          <span>@if (product().priceFrom === product().priceTo) { {{ product().priceFrom | storefrontMoney: currencyCode() }} } @else { Desde {{ product().priceFrom | storefrontMoney: currencyCode() }} }</span>
          @if (context.settings()?.bankTransferEnabled && (context.settings()?.bankTransferDiscountPercentage ?? 0) > 0) {
            <em>{{ context.bankTransferPrice(product().priceFrom) | storefrontMoney: currencyCode() }} transferencia</em>
          }
        </div>
      </div>
    </article>
  `,
  styleUrl: './designer-home.scss',
})
export class DesignerProductTile {
  protected readonly storefrontRouting = inject(StorefrontRoutingService);
  protected readonly context = inject(StorefrontContextService);
  readonly product = input.required<PublicProductSummary>();
  readonly storeSlug = input.required<string>();
  readonly currencyCode = input.required<string>();
  readonly initial = computed(() => this.product().name.trim().slice(0, 1).toUpperCase());
}

@Component({
  selector: 'app-designer-home',
  imports: [RouterLink, DesignerProductTile],
  templateUrl: './designer-home.html',
  styleUrl: './designer-home.scss',
})
export class DesignerHome {
  protected readonly storefrontRouting = inject(StorefrontRoutingService);
  protected readonly context = inject(StorefrontContextService);
  readonly template = input.required<DesignerStorefrontTemplate>();
  readonly categories = input.required<PublicCategory[]>();
  readonly products = input.required<PublicProductSummary[]>();
  readonly storeSlug = input.required<string>();
  readonly currencyCode = input.required<string>();
  readonly heroEyebrow = input.required<string>();
  readonly heroTitle = input.required<string>();
  readonly heroSubtitle = input.required<string>();
  protected readonly storeName = computed(() => this.context.settings()?.storeName ?? 'Tienda');

  protected categoryQuery(category: PublicCategory): Record<string, string> {
    return { categoria: category.slug, catalogo: 'todos' };
  }

  protected catalogQuery(): Record<string, string> {
    return { catalogo: 'todos' };
  }
}
