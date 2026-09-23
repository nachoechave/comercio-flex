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
    <aside class="fresh-topbar"><span>Envíos a todo el país</span><span>3 y 6 cuotas sin interés</span><span>Buenas compras, mejor vibra ✦</span></aside>
    <header class="fresh-header">
      <a class="fresh-brand" [routerLink]="storefrontRouting.route(settings().slug)">
        @if (branding().logoUrl; as logo) { <img class="brand-logo" [src]="logo" [alt]="'Logo de ' + settings().storeName" /> }
        @else { <span class="fresh-wave">≈</span><strong>{{ settings().storeName }}</strong> }
      </a>
      <nav [class.nav-open]="menuOpen()" aria-label="Navegación principal">
        <a [routerLink]="storefrontRouting.route(settings().slug)" fragment="catalog-products">Novedades</a>
        <a [routerLink]="storefrontRouting.route(settings().slug)" fragment="category-section">Categorías</a>
        <a [routerLink]="storefrontRouting.route(settings().slug)" fragment="catalog-products">Productos</a>
        <a class="fresh-offers" [routerLink]="storefrontRouting.route(settings().slug)" fragment="catalog-products">Ofertas</a>
      </nav>
      <a class="fresh-search" [routerLink]="storefrontRouting.route(settings().slug)" fragment="catalog-search">⌕ <span>¿Qué estás buscando?</span></a>
      <a class="fresh-account" [routerLink]="storefrontRouting.route(settings().slug, 'mis-pedidos')" aria-label="Mis pedidos">☺</a>
      <a class="fresh-cart" [routerLink]="storefrontRouting.route(settings().slug, 'carrito')" [attr.aria-label]="'Carrito, ' + cartUnits() + ' unidades'">🛒<span>{{ cartUnits() }}</span></a>
      <button class="mobile-menu" type="button" (click)="toggleMenu()" [attr.aria-expanded]="menuOpen()" aria-label="Abrir navegación">Menú</button>
    </header>
    <app-cart-preview [storeSlug]="settings().slug" />
    <main id="main-content"><router-outlet /></main>
    <section class="fresh-benefits"><span>✦ Envíos rápidos</span><span>▣ Cuotas disponibles</span><span>♡ Compra simple</span><span>☻ Atención cercana</span></section>
    <footer class="fresh-footer">
      <a class="fresh-footer__brand" [routerLink]="storefrontRouting.route(settings().slug)"><span>≈</span><strong>{{ settings().storeName }}</strong></a>
      <div><a [routerLink]="storefrontRouting.route(settings().slug)">Nosotros</a><a [routerLink]="storefrontRouting.route(settings().slug, 'mis-pedidos')">Mis pedidos</a><a [routerLink]="storefrontRouting.route(settings().slug, 'carrito')">Carrito</a></div>
      @if (whatsAppUrl(); as url) { <a class="fresh-whatsapp" [href]="url" target="_blank" rel="noopener">WhatsApp ↗</a> }
      <small>Buenas personas, mejores compras ♡</small>
    </footer>
  `,
  styleUrl: './fresh-storefront-shell.scss',
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
