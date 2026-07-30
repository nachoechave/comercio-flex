import { DatePipe } from '@angular/common';
import { Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map, Subscription } from 'rxjs';

import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { StorefrontApiService } from '../storefront-api.service';
import { storefrontErrorMessage } from '../storefront-errors';
import { StorefrontMoneyPipe } from '../storefront-money.pipe';
import { GuestOrder, GuestOrderItem } from '../storefront.models';

@Component({
  selector: 'app-order-confirmation-page',
  imports: [DatePipe, RouterLink, StorefrontMoneyPipe],
  templateUrl: './order-confirmation-page.html',
  styleUrl: './order-confirmation-page.scss',
})
export class OrderConfirmationPage {
  private readonly api = inject(StorefrontApiService);
  private readonly route = inject(ActivatedRoute);
  protected readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: '',
  });
  protected readonly orderId = toSignal(inheritedRouteParam(this.route, 'orderId'), {
    initialValue: '',
  });
  protected readonly token = toSignal(
    this.route.queryParamMap.pipe(map((params) => params.get('token') ?? '')),
    { initialValue: this.route.snapshot.queryParamMap.get('token') ?? '' },
  );
  protected readonly order = signal<GuestOrder | null>(null);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);

  constructor() {
    effect((onCleanup) => {
      const storeSlug = this.storeSlug();
      const orderId = this.orderId();
      const token = this.token();
      this.order.set(null);
      this.errorMessage.set(null);
      if (!storeSlug || !orderId || !token) {
        this.loading.set(false);
        this.errorMessage.set('El enlace del pedido está incompleto.');
        return;
      }
      this.loading.set(true);
      const subscription: Subscription = this.api.getOrder(storeSlug, orderId, token).subscribe({
        next: (order) => {
          this.order.set(order);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.errorMessage.set(storefrontErrorMessage(error, 'No pudimos recuperar este pedido.'));
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  protected optionLabel(item: GuestOrderItem): string {
    return [item.size && `Talle ${item.size}`, item.color].filter(Boolean).join(' · ');
  }
}
