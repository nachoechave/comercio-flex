import { Component, input, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

import { CartPreview } from '../../cart/cart-preview';
import { StoreSettings, TenantBranding } from '../../storefront.models';

@Component({
  selector: 'app-fashion-storefront-shell',
  imports: [RouterLink, RouterOutlet, CartPreview],
  template: `
    <a class="store-skip-link" href="#main-content">Saltar al contenido</a>
    <aside class="announcement-bar" aria-label="Beneficios de compra"><span>Nueva colección</span><span>Compra online simple y segura</span><span>Atención personalizada</span></aside>
    <header class="site-header site-header--fashion site-header--streetwear">
      <a class="brand brand--fashion" [routerLink]="['/tiendas', settings().slug]">
        @if (branding().logoUrl; as logo) { <img class="brand-logo" [src]="logo" [alt]="'Logo de ' + settings().storeName" /> }
        @else { <span class="fashion-wordmark">{{ settings().storeName }}</span> }
      </a>
      <button class="mobile-menu" type="button" (click)="toggleMenu()" [attr.aria-expanded]="menuOpen()" aria-label="Abrir navegación">Menú</button>
      <nav [class.nav-open]="menuOpen()" aria-label="Navegación principal">
        <a [routerLink]="['/tiendas', settings().slug]">Inicio</a><a [routerLink]="['/tiendas', settings().slug]" fragment="catalog-products">Colección</a><a [routerLink]="['/tiendas', settings().slug]" fragment="category-section">Categorías</a><a [routerLink]="['/tiendas', settings().slug, 'mis-pedidos']">Mis pedidos</a><a class="cart-link" [routerLink]="['/tiendas', settings().slug, 'carrito']" [attr.aria-label]="'Carrito, ' + cartUnits() + ' unidades'">Carrito <span>{{ cartUnits() }}</span></a>
      </nav>
    </header>
    <app-cart-preview [storeSlug]="settings().slug" />
    <main id="main-content"><router-outlet /></main>
    <section class="store-newsletter store-newsletter--fashion"><div><p><strong>Novedades de la tienda</strong><small>Colecciones, productos y beneficios seleccionados.</small></p></div><form><label class="visually-hidden" for="fashion-email">Correo electrónico</label><input id="fashion-email" type="email" placeholder="Tu correo electrónico" /><button type="button">Suscribirme</button></form></section>
    <footer class="site-footer site-footer--fashion"><div class="footer-brand"><strong>{{ settings().storeName }}</strong><p>Una selección con identidad propia.</p></div><div><strong>Explorar</strong><a [routerLink]="['/tiendas', settings().slug]">Productos</a><a [routerLink]="['/tiendas', settings().slug]" fragment="category-section">Colecciones</a></div><div><strong>Tu compra</strong><a [routerLink]="['/tiendas', settings().slug, 'carrito']">Carrito</a><a [routerLink]="['/tiendas', settings().slug, 'mis-pedidos']">Mis pedidos</a></div><div><strong>Contacto</strong>@if (settings().contactPhone) { <a [href]="'tel:' + settings().contactPhone">{{ settings().contactPhone }}</a> }@if (settings().contactEmail) { <a [href]="'mailto:' + settings().contactEmail">{{ settings().contactEmail }}</a> }<small>Creada con Comercio Flex</small></div></footer>
  `,
})
export class FashionStorefrontShell {
  readonly settings = input.required<StoreSettings>();
  readonly branding = input.required<TenantBranding>();
  readonly cartUnits = input.required<number>();
  readonly menuOpen = signal(false);

  toggleMenu(): void {
    this.menuOpen.update((value) => !value);
  }
}
