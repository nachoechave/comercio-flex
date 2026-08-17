import { Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { routeParam } from '../../../../core/auth/auth.guards';
import { inheritedRouteParam } from '../../../../core/routing/inherited-route-param';
import { QuantityFormatPipe } from '../../../../shared/pipes/quantity-format.pipe';
import { variantOptionsLabel } from '../../../../shared/variant-options';
import { InventoryApiService } from '../inventory-api.service';
import { inventoryErrorMessage } from '../inventory-errors';
import { InventoryAvailability, InventoryPage } from '../inventory.models';

const EMPTY_PAGE: InventoryPage = {
  items: [],
  page: 0,
  size: 20,
  totalItems: 0,
  totalPages: 0,
};

@Component({
  selector: 'app-inventory-list',
  imports: [QuantityFormatPipe, ReactiveFormsModule, RouterLink],
  templateUrl: './inventory-list.html',
  styleUrl: './inventory-list.scss',
})
export class InventoryList {
  private readonly api = inject(InventoryApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(FormBuilder);
  private readonly reloadVersion = signal(0);
  private requestedPage = 0;
  private lastStoreSlug: string | null = null;

  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: routeParam(this.route.snapshot, 'storeSlug') ?? '',
  });
  readonly page = signal<InventoryPage>(EMPTY_PAGE);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly filters = this.formBuilder.nonNullable.group({
    q: [''],
    availability: ['ALL' as InventoryAvailability],
  });

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      this.reloadVersion();
      if (slug !== this.lastStoreSlug) {
        this.lastStoreSlug = slug;
        this.requestedPage = 0;
        this.filters.reset({ q: '', availability: 'ALL' });
      }
      this.page.set(EMPTY_PAGE);
      this.loading.set(true);
      this.errorMessage.set(null);
      if (!slug) {
        this.loading.set(false);
        this.errorMessage.set('No pudimos identificar el comercio solicitado.');
        return;
      }

      const filters = this.filters.getRawValue();
      const subscription = this.api
        .list(slug, this.requestedPage, 20, filters.q.trim(), filters.availability)
        .subscribe({
          next: (page) => {
            this.page.set(page);
            this.loading.set(false);
          },
          error: (error: unknown) => {
            this.loading.set(false);
            this.errorMessage.set(
              inventoryErrorMessage(error, 'No pudimos cargar el inventario.'),
            );
          },
        });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  search(): void {
    this.requestedPage = 0;
    this.reloadVersion.update((value) => value + 1);
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.page().totalPages) return;
    this.requestedPage = page;
    this.reloadVersion.update((value) => value + 1);
  }

  optionLabel(item: InventoryPage['items'][number]): string {
    return variantOptionsLabel(item.options, item.size, item.color) || 'Opción estándar';
  }
}
