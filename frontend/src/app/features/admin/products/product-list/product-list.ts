import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';

import { routeParam } from '../../../../core/auth/auth.guards';
import { AuthService } from '../../../../core/auth/auth.service';
import { inheritedRouteParam } from '../../../../core/routing/inherited-route-param';
import { ProductApiService } from '../product-api.service';
import { productErrorMessage } from '../product-errors';
import { ProductCategory, ProductPage, ProductStatus } from '../product.models';

const EMPTY_PAGE: ProductPage = {
  items: [],
  page: 0,
  size: 20,
  totalItems: 0,
  totalPages: 0,
};

@Component({
  selector: 'app-product-list',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './product-list.html',
  styleUrl: './product-list.scss',
})
export class ProductList {
  private readonly api = inject(ProductApiService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(FormBuilder);
  private readonly requestVersion = signal(0);
  private lastStoreSlug: string | null = null;

  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: routeParam(this.route.snapshot, 'storeSlug') ?? '',
  });
  readonly page = signal<ProductPage>(EMPTY_PAGE);
  readonly categories = signal<ProductCategory[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly canManage = computed(() => {
    const role = this.auth.membershipFor(this.storeSlug() ?? '')?.role;
    return role === 'OWNER' || role === 'ADMIN';
  });
  readonly filters = this.formBuilder.nonNullable.group({
    query: [''],
    status: ['' as '' | ProductStatus],
    categoryId: [''],
  });

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      this.requestVersion();
      if (slug !== this.lastStoreSlug) {
        this.lastStoreSlug = slug;
        this.pageRequest = 0;
        this.filters.reset({ query: '', status: '', categoryId: '' });
      }
      this.page.set(EMPTY_PAGE);
      this.categories.set([]);
      this.errorMessage.set(null);
      this.loading.set(true);
      if (!slug) {
        this.loading.set(false);
        this.errorMessage.set('No pudimos identificar el comercio solicitado.');
        return;
      }

      const value = this.filters.getRawValue();
      const subscription = forkJoin({
        page: this.api.list(slug, {
          page: this.pageRequest,
          size: 20,
          query: value.query.trim() || undefined,
          status: value.status || undefined,
          categoryId: value.categoryId || undefined,
        }),
        categories: this.api
          .listCategories(slug)
          .pipe(catchError(() => of([] as ProductCategory[]))),
      }).subscribe({
        next: ({ page, categories }) => {
          this.page.set(page);
          this.categories.set(categories);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.errorMessage.set(
            productErrorMessage(
              error,
              'No pudimos cargar los productos. Revisá tu conexión e intentá nuevamente.',
            ),
          );
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  private pageRequest = 0;

  search(): void {
    this.pageRequest = 0;
    this.requestVersion.update((value) => value + 1);
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.page().totalPages) return;
    this.pageRequest = page;
    this.requestVersion.update((value) => value + 1);
  }

  statusLabel(status: ProductStatus): string {
    return { DRAFT: 'Borrador', PUBLISHED: 'Publicado', ARCHIVED: 'Archivado' }[status];
  }
}
