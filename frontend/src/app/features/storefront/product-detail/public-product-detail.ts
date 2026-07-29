import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { StorefrontApiService } from '../storefront-api.service';
import { StorefrontContextService } from '../storefront-context.service';
import { storefrontErrorMessage } from '../storefront-errors';
import { PublicProductDetail as PublicProductDetailModel, PublicProductVariant } from '../storefront.models';
import { StorefrontMoneyPipe } from '../storefront-money.pipe';

@Component({
  selector: 'app-public-product-detail',
  imports: [RouterLink, StorefrontMoneyPipe],
  templateUrl: './public-product-detail.html',
  styleUrl: './public-product-detail.scss',
})
export class PublicProductDetail {
  private readonly api = inject(StorefrontApiService);
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
  protected readonly available = computed(
    () => this.product()?.variants.some((variant) => variant.available) ?? false,
  );
  protected readonly initial = computed(
    () => this.product()?.name.trim().slice(0, 1).toUpperCase() ?? '',
  );

  constructor() {
    effect((onCleanup) => {
      const storeSlug = this.storeSlug();
      const productSlug = this.productSlug();
      this.retryVersion();
      this.product.set(null);
      this.errorMessage.set(null);
      this.notFound.set(false);
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
          this.errorMessage.set(
            storefrontErrorMessage(error, 'No pudimos cargar este producto.'),
          );
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
