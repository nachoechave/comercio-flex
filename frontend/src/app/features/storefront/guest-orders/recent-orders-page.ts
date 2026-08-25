import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, from, mergeMap, of, Subscription } from 'rxjs';

import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { CommerceDatePipe } from '../../../shared/pipes/commerce-date.pipe';
import { StorefrontApiService } from '../storefront-api.service';
import { StorefrontMoneyPipe } from '../storefront-money.pipe';
import { GuestOrderStatus } from '../storefront.models';
import { GuestOrderHistoryEntry, GuestOrderHistoryService } from './guest-order-history.service';

@Component({
  selector: 'app-recent-orders-page',
  imports: [CommerceDatePipe, RouterLink, StorefrontMoneyPipe],
  templateUrl: './recent-orders-page.html',
  styleUrl: './recent-orders-page.scss',
})
export class RecentOrdersPage {
  private readonly api = inject(StorefrontApiService);
  private readonly history = inject(GuestOrderHistoryService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: '',
  });
  protected readonly entries = signal<GuestOrderHistoryEntry[]>([]);
  protected readonly loading = signal(true);

  constructor() {
    effect((onCleanup) => {
      const storeSlug = this.storeSlug();
      if (!storeSlug) {
        this.loading.set(false);
        return;
      }
      const stored = this.history.list(storeSlug);
      this.entries.set(stored);
      if (stored.length === 0) {
        this.loading.set(false);
        return;
      }

      this.loading.set(true);
      const subscription: Subscription = from(stored).pipe(
        mergeMap((entry) => this.api.getOrder(storeSlug, entry.orderId, entry.lookupToken).pipe(
          catchError((error: unknown) => of({ entry, error })),
        ), 2),
      ).subscribe({
        next: (result) => {
          if ('error' in result) {
            if (result.error instanceof HttpErrorResponse &&
                (result.error.status === 401 || result.error.status === 403 || result.error.status === 404)) {
              this.history.remove(storeSlug, result.entry.orderId);
              this.entries.set(this.history.list(storeSlug));
            }
            return;
          }
          this.history.update(storeSlug, result);
          this.entries.set(this.history.list(storeSlug));
        },
        complete: () => this.loading.set(false),
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  protected open(entry: GuestOrderHistoryEntry): void {
    void this.router.navigate(['/tiendas', entry.storeSlug, 'pedidos', entry.orderId]);
  }

  protected statusLabel(status: GuestOrderStatus): string {
    return {
      PENDING_CONFIRMATION: 'Pendiente de confirmación',
      CONFIRMED: 'Confirmado',
      READY_FOR_PICKUP: 'Listo para retirar',
      COMPLETED: 'Completado',
      REJECTED: 'Rechazado',
      CANCELLED: 'Cancelado',
      EXPIRED: 'Reserva vencida',
    }[status];
  }
}
