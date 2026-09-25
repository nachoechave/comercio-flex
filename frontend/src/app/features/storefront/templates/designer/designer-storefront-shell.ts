import { Component, inject, input, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

import { CartPreview } from '../../cart/cart-preview';
import { StorefrontRoutingService } from '../../storefront-routing.service';
import { StoreSettings, TenantBranding } from '../../storefront.models';
import { DesignerStorefrontTemplate } from '../../storefront-template';

@Component({
  selector: 'app-designer-storefront-shell',
  imports: [RouterLink, RouterOutlet, CartPreview],
  template: `
    <a class="designer-skip-link" href="#main-content">Saltar al contenido</a>
    <aside class="designer-utility"><span>Envíos a todo el país</span><span>Compra segura</span>@if (settings().contactPhone) { <a [href]="'tel:' + settings().contactPhone">Atención {{ settings().contactPhone }}</a> }</aside>
    <header class="designer-header">
      <a class="designer-brand" [routerLink]="storefrontRouting.route(settings().slug)" queryParamsHandling="preserve">
        @if (branding().logoUrl; as logo) { <img [src]="logo" [alt]="'Logo de ' + settings().storeName" /> }
        @else { <strong>{{ settings().storeName }}</strong> }
      </a>
      <nav [class.is-open]="menuOpen()" aria-label="Navegación principal">
        <a [routerLink]="storefrontRouting.route(settings().slug)" fragment="category-section" queryParamsHandling="preserve">Categorías</a>
        <a [routerLink]="storefrontRouting.route(settings().slug)" [queryParams]="{ catalogo: 'todos' }" fragment="catalog-products" queryParamsHandling="merge">Productos</a>
        <a [routerLink]="storefrontRouting.route(settings().slug)" [queryParams]="{ catalogo: 'todos' }" fragment="catalog-products" queryParamsHandling="merge">Novedades</a>
        <a [routerLink]="storefrontRouting.route(settings().slug)" [queryParams]="{ catalogo: 'todos' }" fragment="catalog-products" queryParamsHandling="merge">Colecciones</a>
      </nav>
      <div class="designer-actions">
        <a class="designer-search" [routerLink]="storefrontRouting.route(settings().slug)" fragment="catalog-search" queryParamsHandling="preserve" aria-label="Buscar">⌕</a>
        <a class="designer-account" [routerLink]="storefrontRouting.route(settings().slug, 'mis-pedidos')" queryParamsHandling="preserve" aria-label="Mi cuenta">♙</a>
        <a class="designer-cart" [routerLink]="storefrontRouting.route(settings().slug, 'carrito')" queryParamsHandling="preserve" aria-label="Carrito">▢ <span>{{ cartUnits() }}</span></a>
        <button class="designer-menu" type="button" (click)="toggleMenu()" [attr.aria-expanded]="menuOpen()">Menú</button>
      </div>
    </header>
    <app-cart-preview [storeSlug]="settings().slug" />
    <main id="main-content"><router-outlet /></main>
    <section class="designer-benefits" aria-label="Beneficios">
      <article><b>↗</b><span><strong>Envíos</strong><small>A todo el país</small></span></article>
      <article><b>◇</b><span><strong>Compra segura</strong><small>Pagos protegidos</small></span></article>
      <article><b>↻</b><span><strong>Cambios simples</strong><small>Atención personalizada</small></span></article>
      <article><b>✓</b><span><strong>Stock actualizado</strong><small>Disponibilidad real</small></span></article>
    </section>
    <footer class="designer-footer">
      <div class="designer-footer__lead"><strong>{{ settings().storeName }}</strong><p>{{ footerMessage() }}</p></div>
      <div><b>Comprar</b><a [routerLink]="storefrontRouting.route(settings().slug)" queryParamsHandling="preserve">Inicio</a><a [routerLink]="storefrontRouting.route(settings().slug)" [queryParams]="{ catalogo: 'todos' }" fragment="catalog-products" queryParamsHandling="merge">Catálogo</a></div>
      <div><b>Cuenta</b><a [routerLink]="storefrontRouting.route(settings().slug, 'mis-pedidos')" queryParamsHandling="preserve">Mis pedidos</a><a [routerLink]="storefrontRouting.route(settings().slug, 'carrito')" queryParamsHandling="preserve">Carrito</a></div>
      <div><b>Contacto</b>@if (settings().contactEmail) { <a [href]="'mailto:' + settings().contactEmail">{{ settings().contactEmail }}</a> }@if (settings().pickupAddress) { <span>{{ settings().pickupAddress }}</span> }</div>
    </footer>
  `,
  styleUrl: './designer-storefront-shell.scss',
})
export class DesignerStorefrontShell {
  readonly settings = input.required<StoreSettings>();
  readonly branding = input.required<TenantBranding>();
  readonly cartUnits = input.required<number>();
  readonly template = input.required<DesignerStorefrontTemplate>();
  readonly menuOpen = signal(false);
  protected readonly storefrontRouting = inject(StorefrontRoutingService);

  toggleMenu(): void {
    this.menuOpen.update((value) => !value);
  }

  protected footerMessage(): string {
    switch (this.template()) {
      case 'COAST': return 'Good people. Better places.';
      case 'MINIMAL': return 'Menos, mejor, por más tiempo.';
      case 'LUXE': return 'Belleza, inspiración y exclusividad.';
      case 'URBAN': return 'Misma cultura. Otra generación.';
      case 'EDITORIAL': return 'Marcas, historias y personas.';
      case 'MARKET': return 'Todo lo que necesitás, en un solo lugar.';
      case 'STUDIO': return 'Espacios, objetos y personalidad.';
      case 'BOLD': return 'Equipate. Explorá. Superá.';
    }
  }
}
