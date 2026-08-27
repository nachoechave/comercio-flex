import { Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';

import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { CommerceDatePipe } from '../../../shared/pipes/commerce-date.pipe';
import { QuantityFormatPipe } from '../../../shared/pipes/quantity-format.pipe';
import { StorefrontMoneyPipe } from '../../storefront/storefront-money.pipe';
import { AdminStatCard } from '../../../shared/ui/admin-stat-card/admin-stat-card';
import { BankTransferApiService } from '../bank-transfers/bank-transfer-api.service';
import { AdminBankTransferPayment } from '../bank-transfers/bank-transfer.models';
import { OrderApiService } from '../orders/order-api.service';
import { AdminOrderSummary, ORDER_STATUS_LABELS } from '../orders/order.models';
import { DashboardApiService } from './dashboard-api.service';
import { DashboardSummary } from './dashboard.models';

@Component({
  selector: 'app-admin-dashboard',
  imports: [AdminStatCard, CommerceDatePipe, QuantityFormatPipe, RouterLink, StorefrontMoneyPipe],
  templateUrl: './admin-dashboard.html',
  styleUrl: './admin-dashboard.scss',
})
export class AdminDashboard {
  private readonly api = inject(DashboardApiService);
  private readonly ordersApi = inject(OrderApiService);
  private readonly transfersApi = inject(BankTransferApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly reloadVersion = signal(0);

  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: '',
  });
  readonly summary = signal<DashboardSummary | null>(null);
  readonly recentOrders = signal<AdminOrderSummary[]>([]);
  readonly transfersUnderReview = signal<AdminBankTransferPayment[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly orderLabels = ORDER_STATUS_LABELS;

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      this.reloadVersion();
      this.summary.set(null);
      this.recentOrders.set([]);
      this.transfersUnderReview.set([]);
      this.errorMessage.set(null);
      this.loading.set(true);
      if (!slug) {
        this.loading.set(false);
        this.errorMessage.set('No pudimos identificar el comercio solicitado.');
        return;
      }
      const subscription = forkJoin({
        summary: this.api.get(slug),
        orders: this.ordersApi.list(slug, 0, 5, '', '').pipe(catchError(() => of(null))),
        transfers: this.transfersApi.listPending(slug).pipe(catchError(() => of(null))),
      }).subscribe({
        next: ({ summary, orders, transfers }) => {
          this.summary.set(summary);
          this.recentOrders.set(orders?.items ?? []);
          this.transfersUnderReview.set(
            (transfers ?? []).filter((transfer) => transfer.status === 'UNDER_REVIEW').slice(0, 5),
          );
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.errorMessage.set('No pudimos cargar el resumen operativo.');
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  reload(): void {
    this.reloadVersion.update((value) => value + 1);
  }

  formatMoney(value: string, currencyCode: string): string {
    return new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency: currencyCode,
      minimumFractionDigits: 2,
    }).format(Number(value));
  }
}
