import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { routeParam } from '../../../core/auth/auth.guards';
import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { PosApiService } from '../pos/pos-api.service';
import { PosCatalogItem } from '../pos/pos.models';
import { InventoryApiService } from './inventory-api.service';
import { InventoryOperationsApiService } from './inventory-operations-api.service';
import {
  InventoryTransfer,
  LowStockAlert,
  OperationalInventoryMovement,
} from './inventory-operations.models';
import { StoreBranch } from './inventory.models';

interface TransferCartLine {
  item: PosCatalogItem;
  quantity: number;
}

@Component({
  selector: 'app-inventory-operations-page',
  template: `
    <section class="operations-page">
      <header class="page-header">
        <div>
          <p class="eyebrow">Inventario físico</p>
          <h1>Operaciones de stock</h1>
          <p class="subtitle">Transferencias entre sucursales, alertas y trazabilidad en un solo lugar.</p>
        </div>
        <button type="button" class="secondary" (click)="refreshOperationalViews()" [disabled]="loading()">
          {{ loading() ? 'Actualizando…' : 'Actualizar' }}
        </button>
      </header>

      @if (errorMessage()) {
        <div class="alert error" role="alert">{{ errorMessage() }}</div>
      }
      @if (successMessage()) {
        <div class="alert success" role="status">{{ successMessage() }}</div>
      }

      <section class="summary-grid">
        <article class="metric-card">
          <span>Alertas de stock</span>
          <strong>{{ alerts().length }}</strong>
          <small>Umbral: {{ threshold() }} unidades</small>
        </article>
        <article class="metric-card">
          <span>Sucursales activas</span>
          <strong>{{ activeBranches().length }}</strong>
          <small>Stock visible por local</small>
        </article>
        <article class="metric-card">
          <span>Transferencias recientes</span>
          <strong>{{ transfers().length }}</strong>
          <small>Últimos movimientos entre locales</small>
        </article>
      </section>

      @if (canTransfer()) {
        <section class="panel" aria-labelledby="transfer-title">
          <div class="section-heading">
            <div>
              <p class="eyebrow">Movimiento interno</p>
              <h2 id="transfer-title">Nueva transferencia</h2>
            </div>
            <span class="pill">No modifica el stock total</span>
          </div>

          @if (activeBranches().length < 2) {
            <div class="empty-state">
              <strong>Necesitás al menos dos sucursales activas.</strong>
              <span>Creá otra sucursal para poder mover mercadería entre locales.</span>
            </div>
          } @else {
            <div class="branch-grid">
              <label>
                <span>Desde</span>
                <select [value]="fromBranchId()" (change)="changeSource($any($event.target).value)">
                  @for (branch of activeBranches(); track branch.id) {
                    <option [value]="branch.id">{{ branch.name }}{{ branch.defaultBranch ? ' · Principal' : '' }}</option>
                  }
                </select>
              </label>
              <label>
                <span>Hacia</span>
                <select [value]="toBranchId()" (change)="toBranchId.set($any($event.target).value)">
                  @for (branch of destinationBranches(); track branch.id) {
                    <option [value]="branch.id">{{ branch.name }}{{ branch.defaultBranch ? ' · Principal' : '' }}</option>
                  }
                </select>
              </label>
            </div>

            <div class="search-row">
              <input
                type="search"
                placeholder="Buscar producto o SKU en la sucursal de origen…"
                [value]="searchQuery()"
                (input)="searchQuery.set($any($event.target).value)"
                (keydown.enter)="searchCatalog()"
              />
              <button type="button" class="secondary" (click)="searchCatalog()" [disabled]="catalogLoading()">
                {{ catalogLoading() ? 'Buscando…' : 'Buscar' }}
              </button>
            </div>

            @if (catalog().length > 0) {
              <div class="catalog-list">
                @for (item of catalog(); track item.variantId) {
                  <article class="catalog-row">
                    <div>
                      <strong>{{ item.productName }}</strong>
                      <span>{{ variantLabel(item) }} · SKU {{ item.sku }}</span>
                    </div>
                    <div class="catalog-stock">
                      <span>Disponible</span>
                      <strong>{{ whole(item.availableQuantity) }}</strong>
                    </div>
                    <button type="button" class="dark-button" (click)="addToTransfer(item)" [disabled]="number(item.availableQuantity) < 1">
                      Agregar
                    </button>
                  </article>
                }
              </div>
            }

            <div class="transfer-cart">
              <div class="section-heading compact">
                <h3>Mercadería a transferir</h3>
                <span>{{ transferUnits() }} unidades</span>
              </div>
              @if (cart().length === 0) {
                <p class="muted">Todavía no agregaste productos.</p>
              } @else {
                @for (line of cart(); track line.item.variantId) {
                  <article class="cart-row">
                    <div>
                      <strong>{{ line.item.productName }}</strong>
                      <span>{{ variantLabel(line.item) }}</span>
                    </div>
                    <div class="qty-control">
                      <button type="button" (click)="decrease(line.item.variantId)">−</button>
                      <strong>{{ line.quantity }}</strong>
                      <button type="button" (click)="increase(line.item.variantId)" [disabled]="line.quantity >= number(line.item.availableQuantity)">+</button>
                    </div>
                    <button type="button" class="remove" (click)="remove(line.item.variantId)">Quitar</button>
                  </article>
                }
              }
              <label class="note-field">
                <span>Observación opcional</span>
                <textarea maxlength="500" rows="2" [value]="note()" (input)="note.set($any($event.target).value)" placeholder="Ej.: reposición del local Centro"></textarea>
              </label>
              <button type="button" class="confirm-button" (click)="submitTransfer()" [disabled]="savingTransfer() || cart().length === 0 || !validTransfer()">
                {{ savingTransfer() ? 'Transfiriendo…' : 'Confirmar transferencia' }}
              </button>
            </div>
          }
        </section>
      }

      <section class="panel" aria-labelledby="alerts-title">
        <div class="section-heading">
          <div>
            <p class="eyebrow">Reposición</p>
            <h2 id="alerts-title">Stock bajo por sucursal</h2>
          </div>
          <label class="filter-inline">
            <span>Filtrar local</span>
            <select [value]="filterBranchId()" (change)="changeFilterBranch($any($event.target).value)">
              <option value="">Todas</option>
              @for (branch of activeBranches(); track branch.id) {
                <option [value]="branch.id">{{ branch.name }}</option>
              }
            </select>
          </label>
        </div>

        @if (alerts().length === 0) {
          <div class="empty-state compact"><strong>Sin alertas de stock bajo.</strong></div>
        } @else {
          <div class="table-wrap">
            <table>
              <thead><tr><th>Sucursal</th><th>Producto</th><th>SKU</th><th>Stock</th><th>Estado</th></tr></thead>
              <tbody>
                @for (alert of alerts(); track alert.branchId + ':' + alert.variantId) {
                  <tr>
                    <td>{{ alert.branchName }}</td>
                    <td>{{ alert.productName }}</td>
                    <td>{{ alert.sku }}</td>
                    <td class="number-cell">{{ whole(alert.quantity) }}</td>
                    <td><span class="stock-badge" [class.out]="number(alert.quantity) <= 0">{{ number(alert.quantity) <= 0 ? 'Sin stock' : 'Bajo' }}</span></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>

      <section class="panel" aria-labelledby="history-title">
        <div class="section-heading">
          <div>
            <p class="eyebrow">Trazabilidad</p>
            <h2 id="history-title">Movimientos recientes</h2>
          </div>
          <span class="muted">Entradas, ventas, ajustes y transferencias</span>
        </div>
        @if (movements().length === 0) {
          <div class="empty-state compact"><strong>No hay movimientos para mostrar.</strong></div>
        } @else {
          <div class="table-wrap">
            <table>
              <thead><tr><th>Fecha</th><th>Sucursal</th><th>Producto</th><th>Motivo</th><th>Cambio</th><th>Usuario</th></tr></thead>
              <tbody>
                @for (movement of movements(); track movement.id) {
                  <tr>
                    <td>{{ dateTime(movement.createdAt) }}</td>
                    <td>{{ movement.branchName || 'General' }}</td>
                    <td><strong>{{ movement.productName }}</strong><small>{{ movement.sku }}</small></td>
                    <td>{{ reasonLabel(movement.reason) }}</td>
                    <td class="delta" [class.positive]="number(movement.delta) > 0">{{ signed(movement.delta) }}</td>
                    <td>{{ movement.actorDisplayName }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>

      <section class="panel" aria-labelledby="transfers-history-title">
        <div class="section-heading">
          <div>
            <p class="eyebrow">Sucursales</p>
            <h2 id="transfers-history-title">Transferencias recientes</h2>
          </div>
        </div>
        @if (transfers().length === 0) {
          <div class="empty-state compact"><strong>Todavía no hay transferencias.</strong></div>
        } @else {
          <div class="transfer-history">
            @for (transfer of transfers(); track transfer.id) {
              <article class="transfer-history-row">
                <div class="transfer-route">
                  <strong>{{ transfer.fromBranchName }}</strong>
                  <span>→</span>
                  <strong>{{ transfer.toBranchName }}</strong>
                </div>
                <div><strong>{{ whole(transfer.totalUnits) }}</strong><span> unidades · {{ transfer.itemCount }} productos</span></div>
                <div><span>{{ transfer.actorDisplayName }}</span><small>{{ dateTime(transfer.createdAt) }}</small></div>
              </article>
            }
          </div>
        }
      </section>
    </section>
  `,
  styles: [`
    :host { display:block; }
    .operations-page { display:grid; gap:1.25rem; color:#172033; }
    .page-header,.section-heading,.search-row,.catalog-row,.cart-row,.transfer-history-row,.transfer-route { display:flex; align-items:center; justify-content:space-between; gap:1rem; }
    .page-header { align-items:flex-start; }
    h1,h2,h3,p { margin:0; }
    h1 { font-size:clamp(1.8rem,3vw,2.4rem); }
    h2 { font-size:1.2rem; }
    h3 { font-size:1rem; }
    .eyebrow { margin-bottom:.3rem; font-size:.72rem; font-weight:850; letter-spacing:.09em; text-transform:uppercase; color:#6b7280; }
    .subtitle,.muted,small,.catalog-row span,.cart-row span,.transfer-history-row span { color:#697386; }
    .summary-grid { display:grid; grid-template-columns:repeat(3,1fr); gap:1rem; }
    .metric-card,.panel { border:1px solid #e2e7ef; border-radius:18px; background:#fff; box-shadow:0 8px 28px rgba(15,23,42,.05); }
    .metric-card { display:grid; gap:.25rem; padding:1rem 1.1rem; }
    .metric-card > span { font-size:.8rem; color:#697386; font-weight:750; }
    .metric-card strong { font-size:1.75rem; }
    .panel { padding:1.2rem; display:grid; gap:1rem; }
    .branch-grid { display:grid; grid-template-columns:1fr 1fr; gap:1rem; }
    label { display:grid; gap:.4rem; font-size:.8rem; font-weight:800; color:#4b5563; }
    input,select,textarea { width:100%; box-sizing:border-box; border:1px solid #d8dee9; border-radius:11px; background:#fff; color:#172033; padding:.7rem .8rem; font:inherit; }
    textarea { resize:vertical; }
    .search-row input { flex:1; }
    button { border:0; font:inherit; cursor:pointer; }
    button:disabled { opacity:.5; cursor:not-allowed; }
    .secondary,.dark-button,.confirm-button { border-radius:10px; padding:.72rem 1rem; font-weight:850; }
    .secondary { background:#eef2f7; color:#1f2937; }
    .dark-button,.confirm-button { background:#111827; color:#fff; }
    .confirm-button { width:100%; min-height:48px; }
    .pill { padding:.4rem .7rem; border-radius:999px; background:#eef6ff; color:#245ea8; font-size:.75rem; font-weight:800; }
    .catalog-list,.transfer-cart,.transfer-history { display:grid; gap:.65rem; }
    .catalog-row,.cart-row,.transfer-history-row { border:1px solid #e7ebf1; border-radius:12px; padding:.75rem .85rem; }
    .catalog-row > div:first-child,.cart-row > div:first-child,.transfer-history-row > div { display:grid; gap:.15rem; }
    .catalog-stock { display:grid; justify-items:end; }
    .qty-control { display:inline-flex; align-items:center; gap:.55rem; border:1px solid #dfe4eb; border-radius:999px; padding:.18rem .35rem; }
    .qty-control button { width:28px; height:28px; border-radius:50%; background:#f1f4f8; }
    .remove { background:transparent; color:#9f1239; font-weight:750; }
    .note-field { margin-top:.25rem; }
    .empty-state { display:grid; gap:.3rem; place-items:center; min-height:120px; padding:1rem; border:1px dashed #d8dee9; border-radius:12px; text-align:center; }
    .empty-state.compact { min-height:70px; }
    .filter-inline { display:flex; align-items:center; gap:.55rem; }
    .filter-inline select { min-width:170px; }
    .table-wrap { overflow-x:auto; }
    table { width:100%; border-collapse:collapse; font-size:.84rem; }
    th,td { padding:.72rem .65rem; text-align:left; border-bottom:1px solid #edf0f4; white-space:nowrap; }
    th { font-size:.7rem; text-transform:uppercase; letter-spacing:.05em; color:#697386; }
    td strong,td small { display:block; }
    .number-cell { font-weight:850; }
    .stock-badge { display:inline-flex; border-radius:999px; padding:.25rem .55rem; background:#fff7ed; color:#9a3412; font-weight:800; }
    .stock-badge.out { background:#fff1f2; color:#9f1239; }
    .delta { color:#b42318; font-weight:850; }
    .delta.positive { color:#15803d; }
    .transfer-route { justify-content:flex-start; }
    .transfer-history-row > div:last-child { justify-items:end; }
    .alert { border-radius:12px; padding:.8rem 1rem; font-weight:750; }
    .alert.error { background:#fff1f2; color:#991b1b; border:1px solid #fecaca; }
    .alert.success { background:#f0fdf4; color:#166534; border:1px solid #bbf7d0; }
    @media (max-width:800px) {
      .summary-grid,.branch-grid { grid-template-columns:1fr; }
      .page-header,.section-heading,.catalog-row,.cart-row,.transfer-history-row { align-items:stretch; flex-direction:column; }
      .filter-inline { width:100%; align-items:stretch; }
      .catalog-stock { justify-items:start; }
      .transfer-history-row > div:last-child { justify-items:start; }
    }
  `],
})
export class InventoryOperationsPage {
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly inventoryApi = inject(InventoryApiService);
  private readonly operationsApi = inject(InventoryOperationsApiService);
  private readonly posApi = inject(PosApiService);

  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: routeParam(this.route.snapshot, 'storeSlug') ?? '',
  });
  readonly branches = signal<StoreBranch[]>([]);
  readonly alerts = signal<LowStockAlert[]>([]);
  readonly threshold = signal('0');
  readonly movements = signal<OperationalInventoryMovement[]>([]);
  readonly transfers = signal<InventoryTransfer[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly filterBranchId = signal('');

  readonly fromBranchId = signal('');
  readonly toBranchId = signal('');
  readonly searchQuery = signal('');
  readonly catalog = signal<PosCatalogItem[]>([]);
  readonly catalogLoading = signal(false);
  readonly cart = signal<TransferCartLine[]>([]);
  readonly note = signal('');
  readonly savingTransfer = signal(false);

  readonly activeBranches = computed(() => this.branches().filter((branch) => branch.active));
  readonly destinationBranches = computed(() =>
    this.activeBranches().filter((branch) => branch.id !== this.fromBranchId()),
  );
  readonly canTransfer = computed(() => {
    const role = this.auth.membershipFor(this.storeSlug() ?? '')?.role;
    return role === 'OWNER' || role === 'ADMIN' || role === 'MANAGER' || role === 'STAFF';
  });
  readonly validTransfer = computed(() =>
    Boolean(this.fromBranchId() && this.toBranchId() && this.fromBranchId() !== this.toBranchId()),
  );
  readonly transferUnits = computed(() =>
    this.cart().reduce((total, line) => total + line.quantity, 0),
  );

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      if (!slug) return;
      this.loading.set(true);
      this.errorMessage.set(null);
      const subscription = forkJoin({
        branches: this.inventoryApi.branches(slug),
        alerts: this.operationsApi.alerts(slug, null),
        movements: this.operationsApi.movements(slug, null),
        transfers: this.operationsApi.transfers(slug),
      }).subscribe({
        next: ({ branches, alerts, movements, transfers }) => {
          this.branches.set(branches);
          this.alerts.set(alerts.alerts);
          this.threshold.set(alerts.threshold);
          this.movements.set(movements);
          this.transfers.set(transfers);
          const active = branches.filter((branch) => branch.active);
          if (!this.fromBranchId() && active.length > 0) this.fromBranchId.set(active[0].id);
          if (!this.toBranchId() && active.length > 1) this.toBranchId.set(active[1].id);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.errorMessage.set(this.message(error, 'No pudimos cargar las operaciones de stock.'));
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  refreshOperationalViews(): void {
    const slug = this.storeSlug();
    if (!slug) return;
    this.loading.set(true);
    this.errorMessage.set(null);
    const branchId = this.filterBranchId() || null;
    forkJoin({
      alerts: this.operationsApi.alerts(slug, branchId),
      movements: this.operationsApi.movements(slug, branchId),
      transfers: this.operationsApi.transfers(slug),
    }).subscribe({
      next: ({ alerts, movements, transfers }) => {
        this.alerts.set(alerts.alerts);
        this.threshold.set(alerts.threshold);
        this.movements.set(movements);
        this.transfers.set(transfers);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(this.message(error, 'No pudimos actualizar el inventario.'));
      },
    });
  }

  changeFilterBranch(branchId: string): void {
    this.filterBranchId.set(branchId);
    this.refreshOperationalViews();
  }

  changeSource(branchId: string): void {
    this.fromBranchId.set(branchId);
    this.cart.set([]);
    this.catalog.set([]);
    if (this.toBranchId() === branchId) {
      this.toBranchId.set(this.destinationBranches()[0]?.id ?? '');
    }
  }

  searchCatalog(): void {
    const slug = this.storeSlug();
    const branchId = this.fromBranchId();
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
        this.errorMessage.set(this.message(error, 'No pudimos buscar productos en esa sucursal.'));
      },
    });
  }

  addToTransfer(item: PosCatalogItem): void {
    if (this.number(item.availableQuantity) < 1) return;
    this.cart.update((lines) => {
      const existing = lines.find((line) => line.item.variantId === item.variantId);
      if (!existing) return [...lines, { item, quantity: 1 }];
      if (existing.quantity >= this.number(item.availableQuantity)) return lines;
      return lines.map((line) =>
        line.item.variantId === item.variantId ? { ...line, quantity: line.quantity + 1 } : line,
      );
    });
  }

  increase(variantId: string): void {
    this.cart.update((lines) => lines.map((line) => {
      if (line.item.variantId !== variantId) return line;
      return line.quantity < this.number(line.item.availableQuantity)
        ? { ...line, quantity: line.quantity + 1 }
        : line;
    }));
  }

  decrease(variantId: string): void {
    this.cart.update((lines) => lines
      .map((line) => line.item.variantId === variantId ? { ...line, quantity: line.quantity - 1 } : line)
      .filter((line) => line.quantity > 0));
  }

  remove(variantId: string): void {
    this.cart.update((lines) => lines.filter((line) => line.item.variantId !== variantId));
  }

  submitTransfer(): void {
    const slug = this.storeSlug();
    if (!slug || !this.validTransfer() || this.cart().length === 0) return;
    this.savingTransfer.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.operationsApi.createTransfer(slug, crypto.randomUUID(), {
      fromBranchId: this.fromBranchId(),
      toBranchId: this.toBranchId(),
      items: this.cart().map((line) => ({ variantId: line.item.variantId, quantity: String(line.quantity) })),
      note: this.note().trim() || null,
    }).subscribe({
      next: (transfer) => {
        this.savingTransfer.set(false);
        this.successMessage.set(`Transferencia registrada: ${transfer.fromBranchName} → ${transfer.toBranchName}.`);
        this.cart.set([]);
        this.note.set('');
        this.searchCatalog();
        this.refreshOperationalViews();
      },
      error: (error: unknown) => {
        this.savingTransfer.set(false);
        this.errorMessage.set(this.message(error, 'No pudimos registrar la transferencia.'));
      },
    });
  }

  variantLabel(item: PosCatalogItem): string {
    return [item.size, item.color].filter(Boolean).join(' · ') || 'Opción estándar';
  }

  reasonLabel(reason: OperationalInventoryMovement['reason']): string {
    return ({
      RECEIPT: 'Recepción',
      CORRECTION: 'Corrección',
      DAMAGE: 'Daño o pérdida',
      RETURN: 'Devolución',
      OTHER: 'Otro',
      ORDER_CONFIRMED: 'Pedido confirmado',
      ORDER_CANCELLED: 'Pedido cancelado',
      LOCAL_SALE: 'Venta en local',
      TRANSFER_OUT: 'Transferencia · salida',
      TRANSFER_IN: 'Transferencia · ingreso',
    } satisfies Record<OperationalInventoryMovement['reason'], string>)[reason];
  }

  number(value: string | number): number {
    return Number(value) || 0;
  }

  whole(value: string | number): string {
    return new Intl.NumberFormat('es-AR', { maximumFractionDigits: 3 }).format(this.number(value));
  }

  signed(value: string | number): string {
    const parsed = this.number(value);
    return `${parsed > 0 ? '+' : ''}${this.whole(parsed)}`;
  }

  dateTime(value: string): string {
    return new Intl.DateTimeFormat('es-AR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
  }

  private message(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse) {
      const detail = error.error?.detail;
      if (typeof detail === 'string' && detail.trim()) return detail;
    }
    return fallback;
  }
}
