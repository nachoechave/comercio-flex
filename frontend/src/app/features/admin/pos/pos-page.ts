import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';

import { routeParam } from '../../../core/auth/auth.guards';
import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { InventoryApiService } from '../inventory/inventory-api.service';
import { StoreBranch } from '../inventory/inventory.models';
import { PosApiService } from './pos-api.service';
import { PosCatalogItem, PosPaymentMethod, PosSale } from './pos.models';

interface CartLine {
  item: PosCatalogItem;
  quantity: number;
}

@Component({
  selector: 'app-pos-page',
  template: `
    <section class="pos-page">
      <header class="page-header">
        <div>
          <p class="eyebrow">Venta presencial</p>
          <h1>Nueva venta</h1>
          <p class="subtitle">Registrá una venta del local y descontá el stock de esa sucursal al instante.</p>
        </div>
        <span class="status-pill">POS · Fase 2</span>
      </header>

      @if (errorMessage()) {
        <div class="alert error" role="alert">{{ errorMessage() }}</div>
      }
      @if (successMessage()) {
        <div class="alert success" role="status">{{ successMessage() }}</div>
      }

      <div class="workspace">
        <section class="sale-card" aria-labelledby="sale-title">
          <div class="section-heading">
            <div>
              <p class="eyebrow">Caja</p>
              <h2 id="sale-title">Armar venta</h2>
            </div>
            <strong>{{ cartUnits() }} {{ cartUnits() === 1 ? 'unidad' : 'unidades' }}</strong>
          </div>

          <div class="field-grid">
            <label>
              <span>Sucursal</span>
              <select [value]="selectedBranchId()" (change)="changeBranch($any($event.target).value)">
                @for (branch of activeBranches(); track branch.id) {
                  <option [value]="branch.id">{{ branch.name }}{{ branch.defaultBranch ? ' · Principal' : '' }}</option>
                }
              </select>
            </label>

            <label>
              <span>Medio de pago</span>
              <select [value]="paymentMethod()" (change)="changePayment($any($event.target).value)">
                <option value="CASH">Efectivo</option>
                <option value="BANK_TRANSFER">Transferencia</option>
                <option value="CARD">Tarjeta</option>
                <option value="OTHER">Otro</option>
              </select>
            </label>
          </div>

          <div class="search-row">
            <label class="search-field">
              <span class="sr-only">Buscar producto</span>
              <input
                type="search"
                placeholder="Buscar por nombre o SKU…"
                [value]="searchQuery()"
                (input)="searchQuery.set($any($event.target).value)"
                (keydown.enter)="searchCatalog()"
              />
            </label>
            <button type="button" class="secondary" (click)="searchCatalog()" [disabled]="catalogLoading() || !selectedBranchId()">
              {{ catalogLoading() ? 'Buscando…' : 'Buscar' }}
            </button>
          </div>

          <div class="catalog" aria-live="polite">
            @if (catalogLoading()) {
              <p class="muted">Cargando productos…</p>
            } @else if (catalog().length === 0) {
              <div class="empty-state">
                <strong>No hay productos para mostrar.</strong>
                <span>Probá con otro nombre o SKU, o revisá el stock de la sucursal.</span>
              </div>
            } @else {
              @for (item of catalog(); track item.variantId) {
                <article class="catalog-item">
                  <div>
                    <strong>{{ item.productName }}</strong>
                    <span>{{ variantLabel(item) }} · SKU {{ item.sku }}</span>
                    <small>Stock disponible: {{ integerQuantity(item.availableQuantity) }}</small>
                  </div>
                  <div class="catalog-action">
                    <strong>{{ money(item.unitPrice) }}</strong>
                    <button
                      type="button"
                      class="add-button"
                      (click)="addToCart(item)"
                      [disabled]="number(item.availableQuantity) < 1"
                    >Agregar</button>
                  </div>
                </article>
              }
            }
          </div>
        </section>

        <aside class="cart-card" aria-labelledby="cart-title">
          <div class="section-heading">
            <div>
              <p class="eyebrow">Venta actual</p>
              <h2 id="cart-title">Resumen</h2>
            </div>
            @if (cart().length > 0) {
              <button type="button" class="text-button" (click)="clearCart()">Vaciar</button>
            }
          </div>

          @if (cart().length === 0) {
            <div class="empty-cart">
              <strong>El carrito está vacío</strong>
              <span>Agregá productos desde el buscador para comenzar.</span>
            </div>
          } @else {
            <div class="cart-lines">
              @for (line of cart(); track line.item.variantId) {
                <article class="cart-line">
                  <div class="cart-copy">
                    <strong>{{ line.item.productName }}</strong>
                    <span>{{ variantLabel(line.item) }}</span>
                    <small>{{ money(line.item.unitPrice) }} c/u</small>
                  </div>
                  <div class="quantity-control" aria-label="Cantidad">
                    <button type="button" (click)="decrease(line.item.variantId)">−</button>
                    <strong>{{ line.quantity }}</strong>
                    <button type="button" (click)="increase(line.item.variantId)" [disabled]="line.quantity >= number(line.item.availableQuantity)">+</button>
                  </div>
                  <strong class="line-total">{{ money(number(line.item.unitPrice) * line.quantity) }}</strong>
                  <button type="button" class="remove" (click)="remove(line.item.variantId)" aria-label="Quitar producto">×</button>
                </article>
              }
            </div>

            <div class="total-box">
              <span>Total</span>
              <strong>{{ money(total()) }}</strong>
            </div>
          }

          <button
            type="button"
            class="confirm-button"
            (click)="confirmSale()"
            [disabled]="saving() || cart().length === 0 || !selectedBranchId()"
          >
            {{ saving() ? 'Registrando venta…' : 'Confirmar venta' }}
          </button>
          <p class="atomic-note">Al confirmar, la venta y el descuento de stock se guardan juntos.</p>
        </aside>
      </div>

      <section class="history-card" aria-labelledby="history-title">
        <div class="section-heading">
          <div>
            <p class="eyebrow">Actividad</p>
            <h2 id="history-title">Últimas ventas en local</h2>
          </div>
          <span class="muted">Hasta 50 ventas recientes</span>
        </div>

        @if (historyLoading()) {
          <p class="muted">Cargando historial…</p>
        } @else if (sales().length === 0) {
          <div class="empty-state compact">
            <strong>Todavía no hay ventas presenciales.</strong>
            <span>La primera venta que registres va a aparecer acá.</span>
          </div>
        } @else {
          <div class="table-wrap">
            <table>
              <thead>
                <tr><th>Fecha</th><th>Sucursal</th><th>Vendedor</th><th>Pago</th><th>Productos</th><th>Total</th></tr>
              </thead>
              <tbody>
                @for (sale of sales(); track sale.id) {
                  <tr>
                    <td>{{ dateTime(sale.createdAt) }}</td>
                    <td>{{ sale.branchName }}</td>
                    <td>{{ sale.sellerDisplayName }}</td>
                    <td>{{ paymentLabel(sale.paymentMethod) }}</td>
                    <td>{{ sale.items.length }}</td>
                    <td class="money-cell">{{ money(sale.subtotal) }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>
    </section>
  `,
  styles: [`
    :host { display: block; }
    .pos-page { display: grid; gap: 1.5rem; color: var(--admin-text, #172033); }
    .page-header, .section-heading, .search-row, .catalog-item, .cart-line, .total-box { display: flex; align-items: center; justify-content: space-between; gap: 1rem; }
    .page-header { align-items: flex-start; }
    h1, h2, p { margin: 0; }
    h1 { font-size: clamp(1.75rem, 3vw, 2.4rem); }
    h2 { font-size: 1.2rem; }
    .eyebrow { margin-bottom: .35rem; color: #697386; font-size: .72rem; font-weight: 800; letter-spacing: .1em; text-transform: uppercase; }
    .subtitle, .muted, .catalog-item span, .catalog-item small, .cart-line span, .cart-line small, .atomic-note, .empty-state span, .empty-cart span { color: #697386; }
    .subtitle { margin-top: .45rem; max-width: 760px; }
    .status-pill { border: 1px solid #d8dee9; border-radius: 999px; padding: .45rem .75rem; background: #fff; font-size: .78rem; font-weight: 800; white-space: nowrap; }
    .workspace { display: grid; grid-template-columns: minmax(0, 1.55fr) minmax(320px, .8fr); gap: 1.25rem; align-items: start; }
    .sale-card, .cart-card, .history-card { border: 1px solid #e2e7ef; border-radius: 18px; background: #fff; box-shadow: 0 10px 30px rgba(15, 23, 42, .05); padding: 1.25rem; }
    .cart-card { position: sticky; top: 1rem; }
    .field-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin: 1.25rem 0 1rem; }
    label { display: grid; gap: .45rem; font-size: .82rem; font-weight: 750; }
    input, select { width: 100%; min-height: 44px; border: 1px solid #d8dee9; border-radius: 11px; background: #fff; padding: .7rem .8rem; color: inherit; font: inherit; box-sizing: border-box; }
    input:focus, select:focus { outline: 3px solid rgba(37, 99, 235, .13); border-color: #2563eb; }
    .search-row { margin-bottom: 1rem; }
    .search-field { flex: 1; }
    button { border: 0; font: inherit; cursor: pointer; }
    button:disabled { cursor: not-allowed; opacity: .5; }
    .secondary, .add-button { border-radius: 10px; padding: .72rem 1rem; font-weight: 800; }
    .secondary { background: #eef2f7; color: #1f2937; }
    .add-button { background: #172033; color: #fff; }
    .catalog { display: grid; gap: .6rem; max-height: 430px; overflow: auto; padding-right: .2rem; }
    .catalog-item { border: 1px solid #e8ecf2; border-radius: 13px; padding: .85rem; align-items: flex-start; }
    .catalog-item > div:first-child, .cart-copy { display: grid; gap: .2rem; }
    .catalog-item span, .catalog-item small, .cart-line span, .cart-line small { font-size: .8rem; }
    .catalog-action { display: grid; gap: .5rem; justify-items: end; white-space: nowrap; }
    .cart-lines { display: grid; gap: .75rem; margin-top: 1rem; }
    .cart-line { display: grid; grid-template-columns: minmax(0, 1fr) auto auto auto; border-bottom: 1px solid #edf0f4; padding-bottom: .75rem; }
    .quantity-control { display: inline-flex; align-items: center; gap: .55rem; border: 1px solid #e0e5ed; border-radius: 999px; padding: .2rem .35rem; }
    .quantity-control button { width: 28px; height: 28px; border-radius: 50%; background: #f1f4f8; }
    .remove { width: 30px; height: 30px; border-radius: 50%; background: transparent; color: #8b95a7; font-size: 1.25rem; }
    .line-total { white-space: nowrap; }
    .total-box { margin: 1rem 0; padding: 1rem 0 .25rem; border-top: 1px solid #e3e8ef; font-size: 1.05rem; }
    .total-box strong { font-size: 1.6rem; }
    .confirm-button { width: 100%; min-height: 50px; border-radius: 12px; background: #111827; color: #fff; font-weight: 850; }
    .atomic-note { margin-top: .65rem; text-align: center; font-size: .75rem; }
    .empty-state, .empty-cart { display: grid; gap: .35rem; place-items: center; min-height: 140px; padding: 1rem; text-align: center; border: 1px dashed #d8dee9; border-radius: 13px; }
    .empty-state.compact { min-height: 90px; }
    .text-button { background: transparent; color: #596579; font-weight: 750; }
    .alert { border-radius: 12px; padding: .85rem 1rem; font-weight: 700; }
    .alert.error { border: 1px solid #fecaca; background: #fff1f2; color: #991b1b; }
    .alert.success { border: 1px solid #bbf7d0; background: #f0fdf4; color: #166534; }
    .history-card { display: grid; gap: 1rem; }
    .table-wrap { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; font-size: .85rem; }
    th, td { padding: .75rem .65rem; border-bottom: 1px solid #edf0f4; text-align: left; white-space: nowrap; }
    th { color: #697386; font-size: .72rem; letter-spacing: .05em; text-transform: uppercase; }
    .money-cell { font-weight: 850; }
    .sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
    @media (max-width: 980px) {
      .workspace { grid-template-columns: 1fr; }
      .cart-card { position: static; }
    }
    @media (max-width: 640px) {
      .page-header { display: grid; }
      .field-grid { grid-template-columns: 1fr; }
      .search-row { align-items: stretch; flex-direction: column; }
      .catalog-item { display: grid; }
      .catalog-action { grid-template-columns: 1fr auto; align-items: center; justify-items: stretch; }
      .cart-line { grid-template-columns: 1fr auto; }
      .quantity-control { justify-self: end; }
      .line-total { grid-column: 1; }
      .remove { grid-column: 2; grid-row: 2; justify-self: end; }
    }
  `],
})
export class PosPage {
  private readonly route = inject(ActivatedRoute);
  private readonly inventoryApi = inject(InventoryApiService);
  private readonly posApi = inject(PosApiService);

  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: routeParam(this.route.snapshot, 'storeSlug') ?? '',
  });
  readonly branches = signal<StoreBranch[]>([]);
  readonly selectedBranchId = signal('');
  readonly searchQuery = signal('');
  readonly catalog = signal<PosCatalogItem[]>([]);
  readonly cart = signal<CartLine[]>([]);
  readonly paymentMethod = signal<PosPaymentMethod>('CASH');
  readonly sales = signal<PosSale[]>([]);
  readonly catalogLoading = signal(false);
  readonly historyLoading = signal(false);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  private readonly idempotencyKey = signal<string | null>(null);

  readonly activeBranches = computed(() => this.branches().filter((branch) => branch.active));
  readonly cartUnits = computed(() => this.cart().reduce((total, line) => total + line.quantity, 0));
  readonly total = computed(() =>
    this.cart().reduce((sum, line) => sum + Number(line.item.unitPrice) * line.quantity, 0),
  );

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      if (!slug) return;
      this.historyLoading.set(true);
      this.errorMessage.set(null);
      const subscription = forkJoin({
        branches: this.inventoryApi.branches(slug),
        sales: this.posApi.sales(slug),
      }).subscribe({
        next: ({ branches, sales }) => {
          this.branches.set(branches);
          this.sales.set(sales);
          this.historyLoading.set(false);
          const selected = branches.find((branch) => branch.active && branch.defaultBranch)
            ?? branches.find((branch) => branch.active);
          this.selectedBranchId.set(selected?.id ?? '');
          if (selected) this.searchCatalog();
        },
        error: (error: unknown) => {
          this.historyLoading.set(false);
          this.errorMessage.set(this.errorText(error, 'No pudimos cargar el punto de venta.'));
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  changeBranch(branchId: string): void {
    if (branchId === this.selectedBranchId()) return;
    this.selectedBranchId.set(branchId);
    this.clearCart();
    this.searchCatalog();
  }

  changePayment(value: string): void {
    this.paymentMethod.set(value as PosPaymentMethod);
    this.idempotencyKey.set(null);
  }

  searchCatalog(): void {
    const slug = this.storeSlug();
    const branchId = this.selectedBranchId();
    if (!slug || !branchId) return;
    this.catalogLoading.set(true);
    this.errorMessage.set(null);
    this.posApi.catalog(slug, branchId, this.searchQuery()).subscribe({
      next: (items) => {
        this.catalog.set(items);
        this.catalogLoading.set(false);
      },
      error: (error: unknown) => {
        this.catalogLoading.set(false);
        this.errorMessage.set(this.errorText(error, 'No pudimos buscar productos.'));
      },
    });
  }

  addToCart(item: PosCatalogItem): void {
    const available = Math.floor(Number(item.availableQuantity));
    if (available < 1) return;
    this.cart.update((lines) => {
      const existing = lines.find((line) => line.item.variantId === item.variantId);
      if (!existing) return [...lines, { item, quantity: 1 }];
      if (existing.quantity >= available) return lines;
      return lines.map((line) =>
        line.item.variantId === item.variantId ? { ...line, quantity: line.quantity + 1 } : line,
      );
    });
    this.touchCart();
  }

  increase(variantId: string): void {
    this.cart.update((lines) => lines.map((line) => {
      if (line.item.variantId !== variantId) return line;
      const available = Math.floor(Number(line.item.availableQuantity));
      return line.quantity < available ? { ...line, quantity: line.quantity + 1 } : line;
    }));
    this.touchCart();
  }

  decrease(variantId: string): void {
    this.cart.update((lines) => lines
      .map((line) => line.item.variantId === variantId ? { ...line, quantity: line.quantity - 1 } : line)
      .filter((line) => line.quantity > 0));
    this.touchCart();
  }

  remove(variantId: string): void {
    this.cart.update((lines) => lines.filter((line) => line.item.variantId !== variantId));
    this.touchCart();
  }

  clearCart(): void {
    this.cart.set([]);
    this.idempotencyKey.set(null);
    this.successMessage.set(null);
  }

  confirmSale(): void {
    const slug = this.storeSlug();
    const branchId = this.selectedBranchId();
    if (!slug || !branchId || this.cart().length === 0 || this.saving()) return;

    const key = this.idempotencyKey() ?? crypto.randomUUID();
    this.idempotencyKey.set(key);
    this.saving.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.posApi.createSale(slug, key, {
      branchId,
      paymentMethod: this.paymentMethod(),
      items: this.cart().map((line) => ({
        variantId: line.item.variantId,
        quantity: line.quantity.toFixed(3),
      })),
    }).subscribe({
      next: (sale) => {
        this.saving.set(false);
        this.cart.set([]);
        this.idempotencyKey.set(null);
        this.successMessage.set(`Venta registrada en ${sale.branchName} por ${this.money(sale.subtotal)}.`);
        this.loadHistory();
        this.searchCatalog();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.errorMessage.set(this.errorText(error, 'No pudimos registrar la venta.'));
      },
    });
  }

  private loadHistory(): void {
    const slug = this.storeSlug();
    if (!slug) return;
    this.historyLoading.set(true);
    this.posApi.sales(slug).subscribe({
      next: (sales) => {
        this.sales.set(sales);
        this.historyLoading.set(false);
      },
      error: () => this.historyLoading.set(false),
    });
  }

  private touchCart(): void {
    this.idempotencyKey.set(null);
    this.successMessage.set(null);
  }

  variantLabel(item: PosCatalogItem): string {
    return [item.size, item.color].filter(Boolean).join(' · ') || 'Opción estándar';
  }

  paymentLabel(method: PosPaymentMethod): string {
    return ({
      CASH: 'Efectivo',
      BANK_TRANSFER: 'Transferencia',
      CARD: 'Tarjeta',
      OTHER: 'Otro',
    } as const)[method];
  }

  money(value: string | number): string {
    return new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency: 'ARS',
      maximumFractionDigits: 0,
    }).format(Number(value));
  }

  dateTime(value: string): string {
    return new Intl.DateTimeFormat('es-AR', {
      dateStyle: 'short',
      timeStyle: 'short',
    }).format(new Date(value));
  }

  integerQuantity(value: string): string {
    return String(Math.floor(Number(value)));
  }

  number(value: string): number {
    return Number(value);
  }

  private errorText(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse) {
      const detail = error.error?.detail;
      if (typeof detail === 'string' && detail.trim()) return detail;
    }
    return fallback;
  }
}
