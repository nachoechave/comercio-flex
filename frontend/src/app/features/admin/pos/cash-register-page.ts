import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';

import { routeParam } from '../../../core/auth/auth.guards';
import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { InventoryApiService } from '../inventory/inventory-api.service';
import { StoreBranch } from '../inventory/inventory.models';
import { PosApiService } from './pos-api.service';
import { CashSession } from './pos.models';

@Component({
  selector: 'app-cash-register-page',
  template: `
    <section class="cash-page">
      <header class="page-header">
        <div>
          <p class="eyebrow">Venta presencial</p>
          <h1>Caja</h1>
          <p class="subtitle">Abrí y cerrá la caja de cada sucursal y controlá el efectivo esperado.</p>
        </div>
        <button type="button" class="secondary" (click)="reload()" [disabled]="loading()">
          {{ loading() ? 'Actualizando…' : 'Actualizar' }}
        </button>
      </header>

      @if (errorMessage()) {
        <div class="alert error" role="alert">{{ errorMessage() }}</div>
      }
      @if (successMessage()) {
        <div class="alert success" role="status">{{ successMessage() }}</div>
      }

      <section class="panel branch-panel">
        <label>
          <span>Sucursal</span>
          <select [value]="selectedBranchId()" (change)="changeBranch($any($event.target).value)">
            @for (branch of activeBranches(); track branch.id) {
              <option [value]="branch.id">{{ branch.name }}{{ branch.defaultBranch ? ' · Principal' : '' }}</option>
            }
          </select>
        </label>
      </section>

      @if (current(); as session) {
        <section class="current-grid">
          <article class="panel status-card open">
            <p class="eyebrow">Estado actual</p>
            <h2>Caja abierta</h2>
            <strong>{{ session.branchName }}</strong>
            <span>Abierta por {{ session.openedByDisplayName }}</span>
            <small>{{ dateTime(session.openedAt) }}</small>
          </article>
          <article class="panel metric-card">
            <span>Saldo inicial</span>
            <strong>{{ money(session.openingAmount) }}</strong>
            <small>Base para el arqueo</small>
          </article>
          <article class="panel close-card">
            <div>
              <p class="eyebrow">Cierre</p>
              <h2>Contar efectivo</h2>
            </div>
            <label>
              <span>Efectivo contado</span>
              <input type="number" min="0" step="0.01" [value]="closingAmount()" (input)="closingAmount.set($any($event.target).value)" />
            </label>
            <button type="button" class="danger-button" (click)="closeSession()" [disabled]="saving() || !validMoney(closingAmount())">
              {{ saving() ? 'Cerrando…' : 'Cerrar caja' }}
            </button>
          </article>
        </section>
      } @else {
        <section class="panel open-card">
          <div>
            <p class="eyebrow">Inicio del turno</p>
            <h2>Abrir caja</h2>
            <p class="muted">Indicá cuánto efectivo hay al comenzar. Las ventas en efectivo realizadas hasta el cierre se sumarán al esperado.</p>
          </div>
          <label>
            <span>Saldo inicial</span>
            <input type="number" min="0" step="0.01" [value]="openingAmount()" (input)="openingAmount.set($any($event.target).value)" />
          </label>
          <button type="button" class="primary-button" (click)="openSession()" [disabled]="saving() || !selectedBranchId() || !validMoney(openingAmount())">
            {{ saving() ? 'Abriendo…' : 'Abrir caja' }}
          </button>
        </section>
      }

      @if (lastClosed(); as closed) {
        <section class="result-grid">
          <article class="panel metric-card">
            <span>Efectivo esperado</span>
            <strong>{{ money(closed.expectedCash || '0') }}</strong>
          </article>
          <article class="panel metric-card">
            <span>Efectivo contado</span>
            <strong>{{ money(closed.closingAmount || '0') }}</strong>
          </article>
          <article class="panel metric-card" [class.difference-ok]="number(closed.differenceAmount) === 0" [class.difference-bad]="number(closed.differenceAmount) !== 0">
            <span>Diferencia</span>
            <strong>{{ signedMoney(closed.differenceAmount || '0') }}</strong>
            <small>{{ number(closed.differenceAmount) === 0 ? 'Caja exacta' : 'Revisar arqueo' }}</small>
          </article>
        </section>
      }

      <section class="panel history-panel">
        <div class="section-heading">
          <div>
            <p class="eyebrow">Historial</p>
            <h2>Últimos cierres y aperturas</h2>
          </div>
          <span class="muted">Sucursal seleccionada</span>
        </div>
        @if (sessions().length === 0) {
          <div class="empty-state"><strong>Todavía no hay movimientos de caja.</strong></div>
        } @else {
          <div class="table-wrap">
            <table>
              <thead><tr><th>Apertura</th><th>Usuario</th><th>Inicial</th><th>Estado</th><th>Esperado</th><th>Contado</th><th>Diferencia</th></tr></thead>
              <tbody>
                @for (session of sessions(); track session.id) {
                  <tr>
                    <td>{{ dateTime(session.openedAt) }}</td>
                    <td>{{ session.openedByDisplayName }}</td>
                    <td>{{ money(session.openingAmount) }}</td>
                    <td><span class="status" [class.closed]="session.status === 'CLOSED'">{{ session.status === 'OPEN' ? 'Abierta' : 'Cerrada' }}</span></td>
                    <td>{{ session.expectedCash === null ? '—' : money(session.expectedCash) }}</td>
                    <td>{{ session.closingAmount === null ? '—' : money(session.closingAmount) }}</td>
                    <td [class.negative]="session.differenceAmount !== null && number(session.differenceAmount) !== 0">{{ session.differenceAmount === null ? '—' : signedMoney(session.differenceAmount) }}</td>
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
    :host { display:block; }
    .cash-page { display:grid; gap:1.25rem; color:#172033; }
    .page-header,.section-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:1rem; }
    h1,h2,p { margin:0; }
    h1 { font-size:clamp(1.8rem,3vw,2.4rem); }
    h2 { font-size:1.2rem; }
    .eyebrow { margin-bottom:.3rem; color:#697386; font-size:.72rem; font-weight:850; letter-spacing:.09em; text-transform:uppercase; }
    .subtitle,.muted,.status-card span,small { color:#697386; }
    .panel { border:1px solid #e2e7ef; border-radius:18px; background:#fff; padding:1.2rem; box-shadow:0 8px 28px rgba(15,23,42,.05); }
    .branch-panel { max-width:440px; }
    .current-grid,.result-grid { display:grid; grid-template-columns:repeat(3,1fr); gap:1rem; align-items:stretch; }
    .status-card,.metric-card,.close-card,.open-card { display:grid; gap:.55rem; }
    .status-card.open { border-color:#bbf7d0; background:#f7fff9; }
    .status-card > strong,.metric-card > strong { font-size:1.55rem; }
    .open-card { grid-template-columns:minmax(0,1fr) minmax(220px,.4fr) auto; align-items:end; }
    label { display:grid; gap:.4rem; font-size:.8rem; font-weight:800; color:#4b5563; }
    input,select { width:100%; min-height:44px; box-sizing:border-box; border:1px solid #d8dee9; border-radius:11px; background:#fff; padding:.7rem .8rem; font:inherit; color:#172033; }
    button { border:0; font:inherit; cursor:pointer; }
    button:disabled { opacity:.5; cursor:not-allowed; }
    .secondary,.primary-button,.danger-button { border-radius:10px; padding:.72rem 1rem; font-weight:850; }
    .secondary { background:#eef2f7; color:#1f2937; }
    .primary-button { background:#111827; color:#fff; min-height:44px; }
    .danger-button { background:#9f1239; color:#fff; min-height:44px; }
    .difference-ok { border-color:#bbf7d0; background:#f0fdf4; }
    .difference-bad { border-color:#fed7aa; background:#fff7ed; }
    .alert { border-radius:12px; padding:.8rem 1rem; font-weight:750; }
    .alert.error { background:#fff1f2; color:#991b1b; border:1px solid #fecaca; }
    .alert.success { background:#f0fdf4; color:#166534; border:1px solid #bbf7d0; }
    .history-panel { display:grid; gap:1rem; }
    .table-wrap { overflow-x:auto; }
    table { width:100%; border-collapse:collapse; font-size:.84rem; }
    th,td { padding:.72rem .65rem; text-align:left; border-bottom:1px solid #edf0f4; white-space:nowrap; }
    th { color:#697386; font-size:.7rem; text-transform:uppercase; letter-spacing:.05em; }
    .status { display:inline-flex; border-radius:999px; padding:.25rem .55rem; background:#dcfce7; color:#166534; font-weight:800; }
    .status.closed { background:#eef2f7; color:#4b5563; }
    .negative { color:#b42318; font-weight:850; }
    .empty-state { display:grid; place-items:center; min-height:80px; border:1px dashed #d8dee9; border-radius:12px; }
    @media (max-width:900px) {
      .current-grid,.result-grid { grid-template-columns:1fr; }
      .open-card { grid-template-columns:1fr; align-items:stretch; }
    }
    @media (max-width:600px) {
      .page-header,.section-heading { flex-direction:column; align-items:stretch; }
      .branch-panel { max-width:none; }
    }
  `],
})
export class CashRegisterPage {
  private readonly route = inject(ActivatedRoute);
  private readonly inventoryApi = inject(InventoryApiService);
  private readonly posApi = inject(PosApiService);

  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: routeParam(this.route.snapshot, 'storeSlug') ?? '',
  });
  readonly branches = signal<StoreBranch[]>([]);
  readonly selectedBranchId = signal('');
  readonly current = signal<CashSession | null>(null);
  readonly sessions = signal<CashSession[]>([]);
  readonly lastClosed = signal<CashSession | null>(null);
  readonly openingAmount = signal('0');
  readonly closingAmount = signal('0');
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      if (!slug) return;
      this.loading.set(true);
      const subscription = forkJoin({
        branches: this.inventoryApi.branches(slug),
        sessions: this.posApi.cashSessions(slug, null),
      }).subscribe({
        next: ({ branches, sessions }) => {
          this.branches.set(branches);
          const firstActive = branches.find((branch) => branch.active);
          const selected = this.selectedBranchId() || firstActive?.id || '';
          this.selectedBranchId.set(selected);
          this.sessions.set(selected ? sessions.filter((session) => session.branchId === selected) : sessions);
          this.loading.set(false);
          if (selected) this.loadCurrent(selected);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.errorMessage.set(this.message(error, 'No pudimos cargar la caja.'));
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  activeBranches(): StoreBranch[] {
    return this.branches().filter((branch) => branch.active);
  }

  changeBranch(branchId: string): void {
    this.selectedBranchId.set(branchId);
    this.current.set(null);
    this.lastClosed.set(null);
    this.loadCurrent(branchId);
    this.loadHistory(branchId);
  }

  reload(): void {
    const branchId = this.selectedBranchId();
    if (!branchId) return;
    this.loading.set(true);
    forkJoin({
      current: this.posApi.currentCashSession(this.storeSlug(), branchId),
      sessions: this.posApi.cashSessions(this.storeSlug(), branchId),
    }).subscribe({
      next: ({ current, sessions }) => {
        this.current.set(current);
        this.sessions.set(sessions);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(this.message(error, 'No pudimos actualizar la caja.'));
      },
    });
  }

  openSession(): void {
    const slug = this.storeSlug();
    const branchId = this.selectedBranchId();
    if (!slug || !branchId || !this.validMoney(this.openingAmount())) return;
    this.saving.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.posApi.openCashSession(slug, branchId, this.normalizeMoney(this.openingAmount())).subscribe({
      next: (session) => {
        this.current.set(session);
        this.openingAmount.set('0');
        this.saving.set(false);
        this.successMessage.set(`Caja abierta en ${session.branchName}.`);
        this.loadHistory(branchId);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.errorMessage.set(this.message(error, 'No pudimos abrir la caja.'));
      },
    });
  }

  closeSession(): void {
    const slug = this.storeSlug();
    const session = this.current();
    if (!slug || !session || !this.validMoney(this.closingAmount())) return;
    this.saving.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.posApi.closeCashSession(slug, session.id, this.normalizeMoney(this.closingAmount())).subscribe({
      next: (closed) => {
        this.current.set(null);
        this.lastClosed.set(closed);
        this.closingAmount.set('0');
        this.saving.set(false);
        this.successMessage.set(`Caja cerrada en ${closed.branchName}.`);
        this.loadHistory(closed.branchId);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.errorMessage.set(this.message(error, 'No pudimos cerrar la caja.'));
      },
    });
  }

  validMoney(value: string): boolean {
    const parsed = Number(value);
    return Number.isFinite(parsed) && parsed >= 0;
  }

  number(value: string | null): number {
    return Number(value ?? 0) || 0;
  }

  money(value: string): string {
    return new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' }).format(this.number(value));
  }

  signedMoney(value: string): string {
    const amount = this.number(value);
    const formatted = this.money(String(Math.abs(amount)));
    return `${amount > 0 ? '+' : amount < 0 ? '−' : ''}${formatted}`;
  }

  dateTime(value: string): string {
    return new Intl.DateTimeFormat('es-AR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
  }

  private normalizeMoney(value: string): string {
    return this.number(value).toFixed(2);
  }

  private loadCurrent(branchId: string): void {
    const slug = this.storeSlug();
    if (!slug || !branchId) return;
    this.posApi.currentCashSession(slug, branchId).subscribe({
      next: (session) => this.current.set(session),
      error: (error: unknown) => this.errorMessage.set(this.message(error, 'No pudimos consultar la caja abierta.')),
    });
  }

  private loadHistory(branchId: string): void {
    const slug = this.storeSlug();
    if (!slug || !branchId) return;
    this.posApi.cashSessions(slug, branchId).subscribe({
      next: (sessions) => this.sessions.set(sessions),
      error: (error: unknown) => this.errorMessage.set(this.message(error, 'No pudimos cargar el historial de caja.')),
    });
  }

  private message(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse) {
      const detail = error.error?.detail;
      if (typeof detail === 'string' && detail.trim()) return detail;
    }
    return fallback;
  }
}
