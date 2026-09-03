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
    <aside class="catalog-utility"><span>Compra online segura</span><span>Disponibilidad actualizada</span>@if (settings().contactPhone) { <a [href]="'tel:' + settings().contactPhone">Contacto {{ settings().contactPhone }}</a> }</aside>
    <header class="site-header site-header--catalog"><div class="catalog-main-row"><a class="brand" [routerLink]="storefrontRouting.route(settings().slug)">@if (branding().logoUrl; as logo) { <img class="brand-logo" [src]="logo" [alt]="'Logo de ' + settings().storeName" /> } @else { <span class="brand-mark">{{ settings().storeName.slice(0, 1) }}</span> }<span class="brand-copy"><strong>{{ settings().storeName }}</strong><small>Catálogo online</small></span></a><a class="catalog-search" [routerLink]="storefrontRouting.route(settings().slug)" fragment="catalog-search"><span>¿Qué estás buscando?</span><b>Buscar</b></a><a class="cart-link catalog-cart" [routerLink]="storefrontRouting.route(settings().slug, 'carrito')">Carrito <span>{{ cartUnits() }}</span></a><button class="mobile-menu" type="button" (click)="toggleMenu()" [attr.aria-expanded]="menuOpen()">Menú</button></div><nav [class.nav-open]="menuOpen()" aria-label="Navegación principal"><a [routerLink]="storefrontRouting.route(settings().slug)">Productos</a><a [routerLink]="storefrontRouting.route(settings().slug)" fragment="category-section">Categorías</a><a [routerLink]="storefrontRouting.route(settings().slug)" fragment="catalog-products">Novedades</a><a [routerLink]="storefrontRouting.route(settings().slug, 'mis-pedidos')">Mis pedidos</a></nav></header>
    <app-cart-preview [storeSlug]="settings().slug" /><main id="main-content"><router-outlet /></main>
    <footer class="site-footer site-footer--catalog"><div class="footer-brand"><strong>{{ settings().storeName }}</strong><p>Encontrá productos, variantes y disponibilidad en un solo lugar.</p></div><div><strong>Catálogo</strong><a [routerLink]="storefrontRouting.route(settings().slug)">Productos</a><a [routerLink]="storefrontRouting.route(settings().slug)" fragment="category-section">Categorías</a></div><div><strong>Cuenta</strong><a [routerLink]="storefrontRouting.route(settings().slug, 'mis-pedidos')">Mis pedidos</a><a [routerLink]="storefrontRouting.route(settings().slug, 'carrito')">Carrito</a></div><div><strong>Información</strong>@if (settings().contactEmail) { <a [href]="'mailto:' + settings().contactEmail">Contacto</a> }@if (settings().pickupAddress) { <span>{{ settings().pickupAddress }}</span> }<small>Creada con Comercio Flex</small></div></footer>
  `,
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
