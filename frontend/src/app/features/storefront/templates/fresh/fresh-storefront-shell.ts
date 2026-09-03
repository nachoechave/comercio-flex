import { Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

import { CartPreview } from '../../cart/cart-preview';
import { StorefrontRoutingService } from '../../storefront-routing.service';
import { StoreSettings, TenantBranding } from '../../storefront.models';

@Component({
  selector: 'app-fresh-storefront-shell',
  imports: [RouterLink, RouterOutlet, CartPreview],
  template: `
    <a class="store-skip-link" href="#main-content">Saltar al contenido</a>
    <aside class="fresh-promo"><span>Productos frescos</span><span>Stock actualizado</span><span>Compra directa al comercio</span></aside>
    <header class="site-header site-header--fresh"><div class="fresh-brand-row">
      <a class="brand" [routerLink]="storefrontRouting.route(settings().slug)">@if (branding().logoUrl; as logo) { <img class="brand-logo" [src]="logo" [alt]="'Logo de ' + settings().storeName" /> } @else { <span class="brand-mark">{{ settings().storeName.slice(0, 1) }}</span> }<span class="brand-copy"><strong>{{ settings().storeName }}</strong><small>Mercado de confianza</small></span></a>
      <a class="fresh-search" [routerLink]="storefrontRouting.route(settings().slug)" fragment="catalog-search">⌕ <span>Buscar productos</span></a><a class="cart-link fresh-cart" [routerLink]="storefrontRouting.route(settings().slug, 'carrito')">Mi carrito <span>{{ cartUnits() }}</span></a><button class="mobile-menu" type="button" (click)="toggleMenu()" [attr.aria-expanded]="menuOpen()" aria-label="Abrir navegación">Menú</button>
    </div><nav [class.nav-open]="menuOpen()" aria-label="Navegación principal"><a [routerLink]="storefrontRouting.route(settings().slug)">Inicio</a><a [routerLink]="storefrontRouting.route(settings().slug)" fragment="category-section">Categorías</a><a [routerLink]="storefrontRouting.route(settings().slug)" fragment="catalog-products">Productos</a><a [routerLink]="storefrontRouting.route(settings().slug, 'mis-pedidos')">Mis pedidos</a>@if (whatsAppUrl(); as url) { <a class="whatsapp-link" [href]="url" target="_blank" rel="noopener">WhatsApp</a> }</nav></header>
    <app-cart-preview [storeSlug]="settings().slug" /><main id="main-content"><router-outlet /></main>
    <footer class="site-footer site-footer--fresh"><div class="footer-brand"><strong>{{ settings().storeName }}</strong><p>Calidad, cercanía y atención de siempre.</p>@if (settings().pickupAddress) { <span>{{ settings().pickupAddress }}</span> }</div><div><strong>Comprar</strong><a [routerLink]="storefrontRouting.route(settings().slug)">Todos los productos</a><a [routerLink]="storefrontRouting.route(settings().slug, 'carrito')">Carrito</a></div><div><strong>Ayuda</strong><a [routerLink]="storefrontRouting.route(settings().slug, 'mis-pedidos')">Mis pedidos</a>@if (settings().contactPhone) { <a [href]="'tel:' + settings().contactPhone">Llamanos</a> }</div><div><strong>Comercio Flex</strong><small>Tienda online del comercio</small></div></footer>
  `,
})
export class FreshStorefrontShell {
  protected readonly storefrontRouting = inject(StorefrontRoutingService);
  readonly settings = input.required<StoreSettings>();
  readonly branding = input.required<TenantBranding>();
  readonly cartUnits = input.required<number>();
  readonly menuOpen = signal(false);
  readonly whatsAppUrl = computed(() => {
    const phone = this.settings().contactPhone?.replace(/\D/g, '');
    return phone ? `https://wa.me/${phone}` : null;
  });

  toggleMenu(): void {
    this.menuOpen.update((value) => !value);
  }
}
