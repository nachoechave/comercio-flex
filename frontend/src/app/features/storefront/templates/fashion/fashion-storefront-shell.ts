import { Component, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { CartPreview } from '../../cart/cart-preview';
import { StorefrontRoutingService } from '../../storefront-routing.service';
import { StoreSettings, TenantBranding } from '../../storefront.models';

@Component({
  selector: 'app-fashion-storefront-shell',
  imports: [RouterLink, CartPreview],
  template: `
    <a class="store-skip-link" href="#main-content">Saltar al contenido</a>
    <aside class="announcement-bar" aria-label="Beneficios de compra"><span>Nueva colección</span><span>Compra online simple y segura</span><span>Atención personalizada</span></aside>
    <header class="site-header site-header--fashion site-header--streetwear">
      <a class="brand brand--fashion" [class.brand--limits]="settings().slug === 'limits'" [routerLink]="storefrontRouting.route(settings().slug)">
        @if (branding().logoUrl; as logo) { <img class="brand-logo" [src]="logo" [alt]="'Logo de ' + settings().storeName" /> }
        @else { <span class="fashion-wordmark">{{ settings().storeName }}</span> }
      </a>
      <button class="mobile-menu" type="button" (click)="toggleMenu()" [attr.aria-expanded]="menuOpen()" aria-label="Abrir navegación">Menú</button>
      <nav [class.nav-open]="menuOpen()" aria-label="Navegación principal">
        <a [routerLink]="storefrontRouting.route(settings().slug)">Inicio</a><a [routerLink]="storefrontRouting.route(settings().slug)" fragment="catalog-products">Colección</a><a [routerLink]="storefrontRouting.route(settings().slug)" fragment="category-section">Categorías</a><a [routerLink]="storefrontRouting.route(settings().slug, 'mis-pedidos')">Mis pedidos</a><a class="cart-link" [routerLink]="storefrontRouting.route(settings().slug, 'carrito')" [attr.aria-label]="'Carrito, ' + cartUnits() + ' unidades'">CARRITO <span>{{ cartUnits() }}</span></a>
      </nav>
    </header>
    <app-cart-preview [storeSlug]="settings().slug" />
    <main id="main-content"><ng-content /></main>
    <footer class="site-footer site-footer--fashion site-footer--streetwear"><div class="footer-brand"><strong>{{ settings().storeName }}</strong><p>Una selección con identidad propia.</p></div><div class="footer-explore"><strong>Explorar</strong><a [routerLink]="storefrontRouting.route(settings().slug)">Productos</a><a [routerLink]="storefrontRouting.route(settings().slug)" fragment="category-section">Colecciones</a></div><div class="footer-purchase"><strong>Tu compra</strong><a [routerLink]="storefrontRouting.route(settings().slug, 'carrito')">Carrito</a><a [routerLink]="storefrontRouting.route(settings().slug, 'mis-pedidos')">Mis pedidos</a></div><div class="footer-contact footer-platform"><strong>Contacto</strong>@if (settings().contactPhone) { <a [href]="'tel:' + settings().contactPhone">{{ settings().contactPhone }}</a> }@if (settings().contactEmail) { <a [href]="'mailto:' + settings().contactEmail">{{ settings().contactEmail }}</a> }<small>Creada con Comercio Flex</small></div></footer>
  `,
})
export class FashionStorefrontShell {
  protected readonly storefrontRouting = inject(StorefrontRoutingService);
  readonly settings = input.required<StoreSettings>();
  readonly branding = input.required<TenantBranding>();
  readonly cartUnits = input.required<number>();
  readonly menuOpen = signal(false);

  toggleMenu(): void {
    this.menuOpen.update((value) => !value);
  }
}
