import { DOCUMENT } from '@angular/common';
import { Component, computed, effect, inject, ViewEncapsulation } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';

import { StorefrontApiService } from '../../features/storefront/storefront-api.service';
import { StorefrontContextService } from '../../features/storefront/storefront-context.service';
import { CartService } from '../../features/storefront/cart/cart.service';
import { BrandFont, TenantBranding } from '../../features/storefront/storefront.models';
import { CatalogStorefrontShell } from '../../features/storefront/templates/catalog/catalog-storefront-shell';
import { FashionStorefrontShell } from '../../features/storefront/templates/fashion/fashion-storefront-shell';
import { FreshStorefrontShell } from '../../features/storefront/templates/fresh/fresh-storefront-shell';

const DEFAULT_BRANDING: TenantBranding = {
  primaryColor: '#6D3CE7',
  secondaryColor: '#2A1B4D',
  backgroundColor: '#F7F5FB',
  textColor: '#211A2D',
  font: 'SYSTEM',
  heroTitle: null,
  heroSubtitle: null,
  template: 'CATALOG',
  logoUrl: null,
  faviconUrl: null,
  heroImageUrl: null,
};

@Component({
  selector: 'app-storefront-layout',
  imports: [RouterLink, CatalogStorefrontShell, FashionStorefrontShell, FreshStorefrontShell],
  providers: [StorefrontApiService, StorefrontContextService],
  templateUrl: './storefront-layout.html',
  styleUrl: './storefront-layout.scss',
  encapsulation: ViewEncapsulation.None,
})
export class StorefrontLayout {
  private readonly route = inject(ActivatedRoute);
  private readonly cart = inject(CartService);
  private readonly document = inject(DOCUMENT);
  protected readonly context = inject(StorefrontContextService);
  protected readonly storeSlug = toSignal(
    this.route.paramMap.pipe(map((params) => params.get('storeSlug') ?? '')),
    { initialValue: this.route.snapshot.paramMap.get('storeSlug') ?? '' },
  );
  protected readonly cartUnits = computed(() => this.cart.totalUnits(this.storeSlug()));
  protected readonly branding = computed(() => this.context.settings()?.branding ?? DEFAULT_BRANDING);
  protected readonly fontFamily = computed(() => this.fontStack(this.branding().font));

  constructor() {
    effect(() => {
      const slug = this.storeSlug();
      if (slug) {
        this.cart.activate(slug);
        this.context.load(slug);
      }
    });

    effect((onCleanup) => {
      const faviconUrl = this.context.settings()?.branding?.faviconUrl;
      if (!faviconUrl) return;
      const link = this.document.createElement('link');
      link.rel = 'icon';
      link.href = faviconUrl;
      link.dataset['tenantFavicon'] = 'true';
      this.document.head.appendChild(link);
      onCleanup(() => link.remove());
    });
  }

  private fontStack(font: BrandFont): string {
    if (font === 'SERIF') return "Georgia, 'Times New Roman', serif";
    if (font === 'SANS') return "Inter, 'Segoe UI', Arial, sans-serif";
    return "system-ui, -apple-system, 'Segoe UI', sans-serif";
  }
}
