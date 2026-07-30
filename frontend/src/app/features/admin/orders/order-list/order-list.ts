import { Component, effect, inject, signal } from '@angular/core';
import { DatePipe, KeyValuePipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { inheritedRouteParam } from '../../../../core/routing/inherited-route-param';
import { StorefrontMoneyPipe } from '../../../storefront/storefront-money.pipe';
import { OrderApiService } from '../order-api.service';
import { AdminOrderPage, ORDER_STATUS_LABELS, OrderStatus } from '../order.models';

const EMPTY_PAGE: AdminOrderPage = {
  items: [],
  page: 0,
  size: 20,
  totalItems: 0,
  totalPages: 0,
};

@Component({
  selector: 'app-order-list',
  imports: [DatePipe, KeyValuePipe, ReactiveFormsModule, RouterLink, StorefrontMoneyPipe],
  templateUrl: './order-list.html',
  styleUrl: './order-list.scss',
})
export class OrderList {
  private readonly api = inject(OrderApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(FormBuilder);
  private readonly reloadVersion = signal(0);
  private requestedPage = 0;

  readonly labels = ORDER_STATUS_LABELS;
  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: '',
  });
  readonly page = signal(EMPTY_PAGE);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly filters = this.formBuilder.nonNullable.group({
    q: [''],
    status: ['' as OrderStatus | ''],
  });

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      this.reloadVersion();
      this.loading.set(true);
      this.errorMessage.set(null);
      if (!slug) return;
      const filters = this.filters.getRawValue();
      const subscription = this.api
        .list(slug, this.requestedPage, 20, filters.q.trim(), filters.status)
        .subscribe({
          next: (page) => {
            this.page.set(page);
            this.loading.set(false);
          },
          error: () => {
            this.errorMessage.set('No pudimos cargar los pedidos.');
            this.loading.set(false);
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
}
