import { Meta, Title } from '@angular/platform-browser';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { forkJoin } from 'rxjs';

import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { ProductCard } from '../product-card/product-card';
import { StorefrontApiService } from '../storefront-api.service';
import { StorefrontContextService } from '../storefront-context.service';
import { storefrontErrorMessage } from '../storefront-errors';
import { PublicCategory, PublicProductPage } from '../storefront.models';
import { storefrontVisualIdentityFor } from '../storefront-visual-identity';

const PAGE_SIZE = 24;
const EMPTY_PAGE: PublicProductPage = {
  items: [],
  page: 0,
  size: PAGE_SIZE,
  totalItems: 0,
  totalPages: 0,
};

@Component({
  selector: 'app-catalog-page',
  imports: [ReactiveFormsModule, ProductCard],
  templateUrl: './catalog-page.html',
  styleUrl: './catalog-page.scss',
})
export class CatalogPage {
  private readonly api = inject(StorefrontApiService);
  protected readonly context = inject(StorefrontContextService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly retryVersion = signal(0);

  protected readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: '',
  });
  private readonly queryParams = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });
  protected readonly categories = signal<PublicCategory[]>([]);
  protected readonly page = signal<PublicProductPage>(EMPTY_PAGE);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly query = signal('');
  protected readonly selectedCategory = signal('');
  protected readonly filters = this.formBuilder.nonNullable.group({
    q: [''],
    category: [''],
  });
  protected readonly currencyCode = this.context.currencyCode;
  protected readonly visualIdentity = computed(() =>
    storefrontVisualIdentityFor(this.storeSlug() ?? ''),
  );

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      const params = this.queryParams();
      this.retryVersion();
      const q = (params.get('q') ?? '').trim().slice(0, 100);
      const category = params.get('categoria') ?? '';
      const requestedPage = this.readPage(params);

      this.query.set(q);
      this.selectedCategory.set(category);
      this.filters.setValue({ q, category }, { emitEvent: false });
      this.page.set(EMPTY_PAGE);
      this.categories.set([]);
      this.errorMessage.set(null);
      this.loading.set(true);

      if (!slug) {
        this.loading.set(false);
        this.errorMessage.set('No pudimos identificar la tienda solicitada.');
        return;
      }

      const subscription = forkJoin({
        categories: this.api.listCategories(slug),
        page: this.api.listProducts(slug, {
          page: requestedPage,
          size: PAGE_SIZE,
          q: q || undefined,
          category: category || undefined,
        }),
      }).subscribe({
        next: ({ categories, page }) => {
          if (page.totalPages > 0 && requestedPage >= page.totalPages) {
            void this.router.navigate([], {
              relativeTo: this.route,
              queryParams: { page: page.totalPages === 1 ? null : page.totalPages },
              queryParamsHandling: 'merge',
              replaceUrl: true,
            });
            return;
          }
          this.categories.set(categories);
          this.page.set(page);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.errorMessage.set(
            storefrontErrorMessage(error, 'No pudimos cargar los productos. Intentá nuevamente.'),
          );
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });

    effect(() => {
      const slug = this.storeSlug();
      const storeName = this.context.settings()?.storeName;
      const settingsSlug = this.context.settings()?.slug;
      if (storeName && settingsSlug === slug) {
        this.title.setTitle(`Productos | ${storeName}`);
        this.meta.updateTag({
          name: 'description',
          content: `Explorá el catálogo de ${storeName}, sus precios y opciones disponibles.`,
        });
      } else {
        this.title.setTitle('Catálogo | Comercio Flex');
        this.meta.updateTag({
          name: 'description',
          content: 'Explorá productos y opciones disponibles en Comercio Flex.',
        });
      }
    });
  }

  protected search(): void {
    const value = this.filters.getRawValue();
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        q: value.q.trim() || null,
        categoria: value.category || null,
        page: null,
      },
    });
  }

  protected clearFilters(): void {
    this.filters.reset({ q: '', category: '' });
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { q: null, categoria: null, page: null },
    });
  }

  protected goToPage(page: number): void {
    if (page < 0 || page >= this.page().totalPages) return;
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: page === 0 ? null : page + 1 },
      queryParamsHandling: 'merge',
    });
  }

  protected retry(): void {
    this.retryVersion.update((value) => value + 1);
  }

  private readPage(params: ParamMap): number {
    const rawPage = Number(params.get('page') ?? '1');
    return Number.isSafeInteger(rawPage) && rawPage > 0 ? rawPage - 1 : 0;
  }
}
