import { Component, inject, input, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

import { CartPreview } from '../../cart/cart-preview';
import { StorefrontRoutingService } from '../../storefront-routing.service';
import { StoreSettings, TenantBranding } from '../../storefront.models';

@Component({
  selector: 'app-catalog-storefront-shell',
  imports: [RouterLink, RouterOutlet, CartPreview],
  template: `
    <a class="store-skip-link" href="#main-content">Saltar al contenido</a>
    <aside class="catalog-utility"><span>Compra online segura</span><span>Stock actualizado</span>@if (settings().contactPhone) { <a [href]="'tel:' + settings().contactPhone">Contacto {{ settings().contactPhone }}</a> }</aside>
    <header class="catalog-header">
      <div class="catalog-main-row">
        <a class="catalog-brand" [routerLink]="storefrontRouting.route(settings().slug)">
          @if (branding().logoUrl; as logo) { <img class="brand-logo" [src]="logo" [alt]="'Logo de ' + settings().storeName" /> }
          @else { <strong>{{ settings().storeName }}</strong><small>CATÁLOGO ONLINE</small> }
        </a>
        <a class="catalog-search" [routerLink]="storefrontRouting.route(settings().slug)" fragment="catalog-search"><span>Buscar productos, categorías...</span><b>⌕</b></a>
        <a class="catalog-account" [routerLink]="storefrontRouting.route(settings().slug, 'mis-pedidos')">Mi cuenta</a>
        <a class="catalog-cart" [routerLink]="storefrontRouting.route(settings().slug, 'carrito')">Carrito <span>{{ cartUnits() }}</span></a>
        <button class="mobile-menu" type="button" (click)="toggleMenu()" [attr.aria-expanded]="menuOpen()">Menú</button>
      </div>
      <nav [class.nav-open]="menuOpen()" aria-label="Navegación principal">
        <a [routerLink]="storefrontRouting.route(settings().slug)" fragment="catalog-products">Catálogo</a>
        <a [routerLink]="storefrontRouting.route(settings().slug)" fragment="category-section">Categorías</a>
        <a [routerLink]="storefrontRouting.route(settings().slug)" fragment="catalog-products">Novedades</a>
        <a [routerLink]="storefrontRouting.route(settings().slug)" fragment="catalog-products">Ofertas</a>
      </nav>
    </header>
    <app-cart-preview [storeSlug]="settings().slug" />
    <main id="main-content"><router-outlet /></main>
    <section class="catalog-benefits"><span>▣ Envíos a todo el país</span><span>▤ Cuotas disponibles</span><span>◇ Cambios simples</span><span>✓ Compra segura</span></section>
    <footer class="catalog-footer">
      <div class="catalog-footer__brand"><strong>{{ settings().storeName }}</strong><p>Todo lo que buscás, en un solo lugar.</p></div>
      <div><strong>Comprar</strong><a [routerLink]="storefrontRouting.route(settings().slug)">Catálogo</a><a [routerLink]="storefrontRouting.route(settings().slug)" fragment="category-section">Categorías</a></div>
      <div><strong>Cuenta</strong><a [routerLink]="storefrontRouting.route(settings().slug, 'mis-pedidos')">Mis pedidos</a><a [routerLink]="storefrontRouting.route(settings().slug, 'carrito')">Carrito</a></div>
      <div><strong>Contacto</strong>@if (settings().contactEmail) { <a [href]="'mailto:' + settings().contactEmail">{{ settings().contactEmail }}</a> }@if (settings().pickupAddress) { <span>{{ settings().pickupAddress }}</span> }</div>
    </footer>
  `,
  styleUrl: './catalog-storefront-shell.scss',
})
export class CatalogStorefrontShell {
  readonly settings = input.required<StoreSettings>();
  readonly branding = input.required<TenantBranding>();
  readonly cartUnits = input.required<number>();
  readonly menuOpen = signal(false);
  protected readonly storefrontRouting = inject(StorefrontRoutingService);

  toggleMenu(): void {
    this.menuOpen.update((value) => !value);
  }
}
