import { Component, inject, input, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

import { CartPreview } from '../../cart/cart-preview';
import { StorefrontRoutingService } from '../../storefront-routing.service';
import { StoreSettings, TenantBranding } from '../../storefront.models';

@Component({
  selector: 'app-fashion-storefront-shell',
  imports: [RouterLink, RouterOutlet, CartPreview],
  template: `
    <a class="store-skip-link" href="#main-content">Saltar al contenido</a>
    <aside class="fashion-utility"><span>Envíos a todo el país</span><span>Cambios simples</span></aside>
    <header class="fashion-header">
      <a class="fashion-brand" [routerLink]="storefrontRouting.route(settings().slug)">
        @if (branding().logoUrl; as logo) { <img class="brand-logo" [src]="logo" [alt]="'Logo de ' + settings().storeName" /> }
        @else { <span>{{ settings().storeName }}</span><small>GOOD VIBES ALWAYS</small> }
      </a>
      <button class="mobile-menu" type="button" (click)="toggleMenu()" [attr.aria-expanded]="menuOpen()" aria-label="Abrir navegación">Menú</button>
      <nav [class.nav-open]="menuOpen()" aria-label="Navegación principal">
        <a [routerLink]="storefrontRouting.route(settings().slug)" fragment="catalog-products">Nueva colección</a>
        <a [routerLink]="storefrontRouting.route(settings().slug)" fragment="category-section">Categorías</a>
        <a [routerLink]="storefrontRouting.route(settings().slug)" fragment="catalog-products">Novedades</a>
        <a [routerLink]="storefrontRouting.route(settings().slug, 'mis-pedidos')">Mis pedidos</a>
        <a class="fashion-cart" [routerLink]="storefrontRouting.route(settings().slug, 'carrito')" [attr.aria-label]="'Carrito, ' + cartUnits() + ' unidades'">Bolsa <span>{{ cartUnits() }}</span></a>
      </nav>
    </header>
    <app-cart-preview [storeSlug]="settings().slug" />
    <main id="main-content"><router-outlet /></main>
    <section class="fashion-newsletter">
      <div><strong>Sumate a la comunidad</strong><small>Recibí novedades, lanzamientos y beneficios.</small></div>
      <form><label class="visually-hidden" for="fashion-email">Correo electrónico</label><input id="fashion-email" type="email" placeholder="Tu email" /><button type="button">Suscribirme</button></form>
    </section>
    <footer class="fashion-footer">
      <div class="fashion-footer__brand"><strong>{{ settings().storeName }}</strong><p>Una tienda con identidad propia.</p></div>
      <div><strong>Tienda</strong><a [routerLink]="storefrontRouting.route(settings().slug)">Nueva colección</a><a [routerLink]="storefrontRouting.route(settings().slug)" fragment="category-section">Categorías</a></div>
      <div><strong>Ayuda</strong><a [routerLink]="storefrontRouting.route(settings().slug, 'mis-pedidos')">Mis pedidos</a><a [routerLink]="storefrontRouting.route(settings().slug, 'carrito')">Carrito</a></div>
      <div><strong>Contacto</strong>@if (settings().contactEmail) { <a [href]="'mailto:' + settings().contactEmail">{{ settings().contactEmail }}</a> }<small>Creada con Comercio Flex</small></div>
    </footer>
  `,
  styleUrl: './fashion-storefront-shell.scss',
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
