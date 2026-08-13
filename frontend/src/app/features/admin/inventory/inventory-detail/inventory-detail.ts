import { DatePipe } from '@angular/common';
import { Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { routeParam } from '../../../../core/auth/auth.guards';
import { inheritedRouteParam } from '../../../../core/routing/inherited-route-param';
import { QuantityFormatPipe } from '../../../../shared/pipes/quantity-format.pipe';
import { InventoryApiService } from '../inventory-api.service';
import { inventoryErrorMessage } from '../inventory-errors';
import { InventoryItem, InventoryMovementReason, MovementPage } from '../inventory.models';

const EMPTY_MOVEMENTS: MovementPage = {
  items: [],
  page: 0,
  size: 20,
  totalItems: 0,
  totalPages: 0,
};

@Component({
  selector: 'app-inventory-detail',
  imports: [DatePipe, QuantityFormatPipe, RouterLink],
  templateUrl: './inventory-detail.html',
  styleUrl: './inventory-detail.scss',
})
export class InventoryDetail {
  private readonly api = inject(InventoryApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly reloadVersion = signal(0);
  private requestedPage = 0;
  private lastContext: string | null = null;

  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: routeParam(this.route.snapshot, 'storeSlug') ?? '',
  });
  readonly variantId = toSignal(inheritedRouteParam(this.route, 'variantId'), {
    initialValue: routeParam(this.route.snapshot, 'variantId'),
  });
  readonly inventory = signal<InventoryItem | null>(null);
  readonly movements = signal<MovementPage>(EMPTY_MOVEMENTS);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      const variantId = this.variantId();
      this.reloadVersion();
      const context = `${slug ?? ''}\u0000${variantId ?? ''}`;
      if (context !== this.lastContext) {
        this.lastContext = context;
        this.requestedPage = 0;
      }
      this.inventory.set(null);
      this.movements.set(EMPTY_MOVEMENTS);
      this.loading.set(true);
      this.errorMessage.set(null);
      if (!slug || !variantId) {
        this.loading.set(false);
        this.errorMessage.set('No pudimos identificar la variante solicitada.');
        return;
      }

      const subscription = forkJoin({
        inventory: this.api.get(slug, variantId),
        movements: this.api.movements(slug, variantId, this.requestedPage, 20),
      }).subscribe({
        next: ({ inventory, movements }) => {
          this.inventory.set(inventory);
          this.movements.set(movements);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.errorMessage.set(
            inventoryErrorMessage(error, 'No pudimos cargar el inventario y sus movimientos.'),
          );
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.movements().totalPages) return;
    this.requestedPage = page;
    this.reloadVersion.update((value) => value + 1);
  }

  reasonLabel(reason: InventoryMovementReason): string {
    return {
      RECEIPT: 'Recepción',
      CORRECTION: 'Corrección',
      DAMAGE: 'Daño o pérdida',
      RETURN: 'Devolución',
      OTHER: 'Otro',
      ORDER_CONFIRMED: 'Pedido confirmado',
      ORDER_CANCELLED: 'Pedido cancelado',
    }[reason];
  }
}
