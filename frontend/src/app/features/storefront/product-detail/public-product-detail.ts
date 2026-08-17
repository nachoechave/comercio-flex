import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { StorefrontApiService } from '../storefront-api.service';
import { StorefrontContextService } from '../storefront-context.service';
import { storefrontErrorMessage } from '../storefront-errors';
import {
  PublicProductDetail as PublicProductDetailModel,
  PublicProductVariant,
} from '../storefront.models';
import { StorefrontMoneyPipe } from '../storefront-money.pipe';
import { CartService } from '../cart/cart.service';

@Component({
  selector: 'app-public-product-detail',
  imports: [RouterLink, StorefrontMoneyPipe],
  templateUrl: './public-product-detail.html',
  styleUrl: './public-product-detail.scss',
})
export class PublicProductDetail {
  private readonly api = inject(StorefrontApiService);
  private readonly cart = inject(CartService);
  protected readonly context = inject(StorefrontContextService);
  private readonly route = inject(ActivatedRoute);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly retryVersion = signal(0);

  protected readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: '',
  });
  protected readonly productSlug = toSignal(inheritedRouteParam(this.route, 'productSlug'), {
    initialValue: '',
  });
  protected readonly product = signal<PublicProductDetailModel | null>(null);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly notFound = signal(false);
  protected readonly selectedVariantId = signal<string | null>(null);
  protected readonly quantity = signal(1);
  protected readonly cartMessage = signal('');
  protected readonly available = computed(
    () => this.product()?.variants.some((variant) => variant.available) ?? false,
  );
  protected readonly isStreetwear = computed(() => this.storeSlug() === 'tienda-a');
  protected readonly featuredPrice = computed(
    () =>
      this.selectedVariant()?.price ??
      this.product()?.variants.find((variant) => variant.available)?.price ??
      this.product()?.variants[0]?.price ??
      null,
  );
  protected readonly initial = computed(
    () => this.product()?.name.trim().slice(0, 1).toUpperCase() ?? '',
  );
  protected readonly selectedVariant = computed(() =>
    this.product()?.variants.find((variant) => variant.id === this.selectedVariantId()),
  );

  constructor() {
    effect((onCleanup) => {
      const storeSlug = this.storeSlug();
      const productSlug = this.productSlug();
      this.retryVersion();
      this.product.set(null);
      this.errorMessage.set(null);
      this.notFound.set(false);
      this.selectedVariantId.set(null);
      this.quantity.set(1);
      this.cartMessage.set('');
      this.loading.set(true);

      if (!storeSlug || !productSlug) {
        this.loading.set(false);
        this.notFound.set(true);
        this.errorMessage.set('No pudimos identificar el producto solicitado.');
        return;
      }

      const subscription = this.api.getProduct(storeSlug, productSlug).subscribe({
        next: (product) => {
          this.product.set(product);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.notFound.set(error instanceof HttpErrorResponse && error.status === 404);
          this.errorMessage.set(storefrontErrorMessage(error, 'No pudimos cargar este producto.'));
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });

    effect(() => {
      const product = this.product();
      const settings = this.context.settings();
      const storeSlug = this.storeSlug();
      if (product && settings?.slug === storeSlug) {
        this.updateMetadata(product, settings.storeName);
      } else {
        this.title.setTitle('Producto | Comercio Flex');
        this.meta.updateTag({
          name: 'description',
          content: 'Consultá productos, opciones y disponibilidad en Comercio Flex.',
        });
      }
    });
  }

  protected retry(): void {
    this.retryVersion.update((value) => value + 1);
  }

  protected variantLabel(variant: PublicProductVariant): string {
    const attributes = [variant.size && `Talle ${variant.size}`, variant.color].filter(Boolean);
    return attributes.length ? attributes.join(' · ') : 'Opción estándar';
  }

  protected selectVariant(variant: PublicProductVariant): void {
    if (!variant.available) return;
    this.selectedVariantId.set(variant.id);
    this.cartMessage.set('');
  }

  protected updateQuantity(event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = input.valueAsNumber;
    if (!Number.isInteger(value) || value < 1 || value > 99) {
      input.value = String(this.quantity());
      this.cartMessage.set('La cantidad debe ser un número entero entre 1 y 99.');
      return;
    }
    this.quantity.set(value);
    this.cartMessage.set('');
  }

  protected addToCart(): void {
    const product = this.product();
    const variant = this.selectedVariant();
    if (!product || !variant) {
      this.cartMessage.set('Elegí una variante disponible.');
      return;
    }

    const result = this.cart.add(this.storeSlug() ?? '', {
      product,
      variant,
      quantity: this.quantity(),
    });
    this.cartMessage.set(
      result.reachedLimit
        ? `El carrito admite hasta 99 unidades de ${this.variantLabel(variant)}.`
        : `Agregamos ${this.quantity()} ${this.quantity() === 1 ? 'unidad' : 'unidades'} al carrito.`,
    );
  }

  private updateMetadata(product: PublicProductDetailModel, storeName: string): void {
    this.title.setTitle(`${product.name} | ${storeName}`);
    const description =
      product.description?.trim() ||
      `Conocé ${product.name}, sus opciones y disponibilidad en ${storeName}.`;
    this.meta.updateTag({
      name: 'description',
      content: description.length > 155 ? `${description.slice(0, 152)}…` : description,
    });
  }
}
