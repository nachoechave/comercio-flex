import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';

import { inheritedRouteParam } from '../../core/routing/inherited-route-param';
import { StoreAnalyticsService, StoreAnalyticsSummary } from './store-analytics.service';

@Component({
  selector: 'app-analytics-dashboard-page',
  template: `
    <section class="analytics-page" aria-labelledby="analytics-title">
      <header class="hero">
        <div>
          <p class="eyebrow">ESTADÍSTICAS</p>
          <h1 id="analytics-title">Tráfico y conversión</h1>
          <p>Entendé cómo llegan los visitantes y qué hacen antes de comprar.</p>
        </div>
        <div class="periods" aria-label="Período de estadísticas">
          <button type="button" [class.active]="period() === 1" (click)="period.set(1)">Hoy</button>
          <button type="button" [class.active]="period() === 7" (click)="period.set(7)">7 días</button>
          <button type="button" [class.active]="period() === 30" (click)="period.set(30)">30 días</button>
        </div>
      </header>

      @if (loading()) {
        <div class="state" role="status">Calculando estadísticas…</div>
      } @else if (errorMessage()) {
        <div class="state error" role="alert">
          <strong>No pudimos cargar las estadísticas.</strong>
          <span>{{ errorMessage() }}</span>
          <button type="button" (click)="reload()">Reintentar</button>
        </div>
      } @else if (summary(); as data) {
        <div class="metrics">
          <article class="metric"><span>Visitas</span><strong>{{ number(data.visits) }}</strong><small>Sesiones de 30 min</small></article>
          <article class="metric"><span>Visitantes</span><strong>{{ number(data.visitors) }}</strong><small>Navegadores únicos aprox.</small></article>
          <article class="metric"><span>Productos vistos</span><strong>{{ number(data.productViews) }}</strong><small>{{ number(data.pageViews) }} páginas vistas</small></article>
          <article class="metric"><span>Agregados al carrito</span><strong>{{ number(data.addToCarts) }}</strong><small>Acciones de intención</small></article>
          <article class="metric"><span>Checkouts</span><strong>{{ number(data.checkouts) }}</strong><small>Compras iniciadas</small></article>
          <article class="metric"><span>Ventas</span><strong>{{ number(data.purchases) }}</strong><small>Pedidos confirmados</small></article>
          <article class="metric conversion"><span>Conversión</span><strong>{{ percent(data.conversionRate) }}</strong><small>Ventas / visitas</small></article>
        </div>

        <div class="content-grid">
          <section class="panel funnel" aria-labelledby="funnel-title">
            <div class="panel-heading">
              <div><p class="eyebrow">EMBUDO</p><h2 id="funnel-title">Recorrido de compra</h2></div>
              <span>{{ periodLabel() }}</span>
            </div>
            <div class="funnel-list">
              <div class="funnel-row">
                <div><strong>Visitas</strong><span>{{ number(data.visits) }}</span></div>
                <div class="bar"><i style="width: 100%"></i></div>
              </div>
              <div class="funnel-row">
                <div><strong>Productos vistos</strong><span>{{ number(data.productViews) }}</span></div>
                <div class="bar"><i [style.width.%]="barWidth(data.productViews, funnelMax())"></i></div>
              </div>
              <div class="funnel-row">
                <div><strong>Agregados al carrito</strong><span>{{ number(data.addToCarts) }}</span></div>
                <div class="bar"><i [style.width.%]="barWidth(data.addToCarts, funnelMax())"></i></div>
              </div>
              <div class="funnel-row">
                <div><strong>Checkouts</strong><span>{{ number(data.checkouts) }}</span></div>
                <div class="bar"><i [style.width.%]="barWidth(data.checkouts, funnelMax())"></i></div>
              </div>
              <div class="funnel-row">
                <div><strong>Ventas confirmadas</strong><span>{{ number(data.purchases) }}</span></div>
                <div class="bar"><i [style.width.%]="barWidth(data.purchases, funnelMax())"></i></div>
              </div>
            </div>
          </section>

          <section class="panel" aria-labelledby="sources-title">
            <div class="panel-heading"><div><p class="eyebrow">ADQUISICIÓN</p><h2 id="sources-title">Origen del tráfico</h2></div></div>
            @if (data.trafficSources.length === 0) {
              <p class="empty">Todavía no hay fuentes de tráfico registradas.</p>
            } @else {
              <div class="source-list">
                @for (source of data.trafficSources; track source.source) {
                  <div class="source-row">
                    <div><strong>{{ source.source }}</strong><span>{{ number(source.visits) }} visitas</span></div>
                    <div class="bar"><i [style.width.%]="barWidth(source.visits, maxSourceVisits())"></i></div>
                  </div>
                }
              </div>
            }
          </section>

          <section class="panel products-panel" aria-labelledby="products-title">
            <div class="panel-heading"><div><p class="eyebrow">INTERÉS</p><h2 id="products-title">Productos más vistos</h2></div></div>
            @if (data.topProducts.length === 0) {
              <p class="empty">Todavía no hay vistas de productos en este período.</p>
            } @else {
              <div class="table-wrap">
                <table>
                  <thead><tr><th>Producto</th><th>Vistas</th><th>Al carrito</th><th>Interés</th></tr></thead>
                  <tbody>
                    @for (product of data.topProducts; track product.productId) {
                      <tr>
                        <td><strong>{{ product.productName }}</strong></td>
                        <td>{{ number(product.views) }}</td>
                        <td>{{ number(product.addToCarts) }}</td>
                        <td>{{ ratio(product.addToCarts, product.views) }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </section>
        </div>

        <footer class="privacy-note">
          <strong>Analítica propia de Comercio Flex</strong>
          <span>No usa servicios pagos ni guarda IP, nombre o email del visitante. Los visitantes son estimaciones anónimas del navegador.</span>
          <small>Zona horaria: {{ data.timezone }}</small>
        </footer>
      }
    </section>
  `,
  styles: [`
    :host { display: block; }
    .analytics-page { display: grid; gap: 22px; max-width: 1380px; margin: 0 auto; }
    .hero { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; padding: 8px 0 2px; }
    .hero h1 { margin: 3px 0 6px; font-size: clamp(1.65rem, 3vw, 2.35rem); color: #101828; letter-spacing: -.035em; }
    .hero p { margin: 0; color: #667085; }
    .eyebrow { margin: 0; font-size: .7rem; font-weight: 850; letter-spacing: .13em; color: #667085; }
    .periods { display: inline-flex; padding: 4px; background: #f2f4f7; border-radius: 12px; gap: 3px; }
    .periods button { border: 0; background: transparent; color: #475467; font: inherit; font-weight: 750; padding: 9px 14px; border-radius: 9px; cursor: pointer; }
    .periods button.active { background: #fff; color: #101828; box-shadow: 0 1px 3px rgb(16 24 40 / 12%); }
    .metrics { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 12px; }
    .metric { min-width: 0; padding: 17px; background: #fff; border: 1px solid #e4e7ec; border-radius: 16px; display: grid; gap: 4px; }
    .metric span { color: #667085; font-size: .78rem; font-weight: 750; }
    .metric strong { color: #101828; font-size: 1.7rem; letter-spacing: -.035em; }
    .metric small { color: #98a2b3; font-size: .7rem; }
    .metric.conversion { background: linear-gradient(145deg, #172033, #283750); border-color: #172033; }
    .metric.conversion span, .metric.conversion small { color: #cbd5e1; }
    .metric.conversion strong { color: #fff; }
    .content-grid { display: grid; grid-template-columns: 1.15fr .85fr; gap: 16px; }
    .panel { background: #fff; border: 1px solid #e4e7ec; border-radius: 18px; padding: 21px; min-width: 0; }
    .products-panel { grid-column: 1 / -1; }
    .panel-heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 18px; }
    .panel-heading h2 { margin: 3px 0 0; color: #101828; font-size: 1.05rem; }
    .panel-heading > span { color: #667085; font-size: .8rem; }
    .funnel-list, .source-list { display: grid; gap: 16px; }
    .funnel-row > div:first-child, .source-row > div:first-child { display: flex; justify-content: space-between; gap: 16px; margin-bottom: 7px; }
    .funnel-row strong, .source-row strong { color: #344054; font-size: .86rem; }
    .funnel-row span, .source-row span { color: #667085; font-size: .8rem; }
    .bar { width: 100%; height: 8px; background: #f2f4f7; border-radius: 999px; overflow: hidden; }
    .bar i { display: block; height: 100%; min-width: 2px; border-radius: inherit; background: linear-gradient(90deg, #344054, #667085); }
    .table-wrap { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; }
    th, td { text-align: left; padding: 12px 10px; border-bottom: 1px solid #eaecf0; color: #475467; font-size: .84rem; }
    th { color: #667085; font-size: .72rem; text-transform: uppercase; letter-spacing: .06em; }
    td strong { color: #101828; }
    .empty { color: #667085; margin: 8px 0; }
    .state { padding: 22px; border: 1px solid #e4e7ec; border-radius: 16px; background: #fff; color: #667085; }
    .state.error { display: grid; gap: 8px; color: #9b1c1c; background: #fff7f7; }
    .state button { justify-self: start; border: 1px solid #d0d5dd; background: #fff; border-radius: 9px; padding: 8px 12px; cursor: pointer; }
    .privacy-note { display: flex; align-items: center; gap: 10px 18px; flex-wrap: wrap; padding: 15px 18px; background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 14px; color: #667085; font-size: .78rem; }
    .privacy-note strong { color: #344054; }
    .privacy-note small { margin-left: auto; color: #98a2b3; }
    @media (max-width: 1180px) { .metrics { grid-template-columns: repeat(4, minmax(0, 1fr)); } }
    @media (max-width: 820px) {
      .hero { align-items: stretch; flex-direction: column; }
      .periods { align-self: flex-start; }
      .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      .content-grid { grid-template-columns: 1fr; }
      .products-panel { grid-column: auto; }
    }
    @media (max-width: 460px) { .metrics { grid-template-columns: 1fr; } .periods { width: 100%; } .periods button { flex: 1; } }
  `],
})
export class AnalyticsDashboardPage {
  private readonly analytics = inject(StoreAnalyticsService);
  private readonly route = inject(ActivatedRoute);
  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), { initialValue: '' });
  readonly period = signal<1 | 7 | 30>(7);
  readonly summary = signal<StoreAnalyticsSummary | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal('');
  private readonly reloadVersion = signal(0);

  readonly funnelMax = computed(() => {
    const data = this.summary();
    if (!data) return 1;
    return Math.max(data.visits, data.productViews, data.addToCarts, data.checkouts, data.purchases, 1);
  });

  readonly maxSourceVisits = computed(() =>
    Math.max(...(this.summary()?.trafficSources.map((item) => item.visits) ?? [1]), 1),
  );

  readonly periodLabel = computed(() =>
    this.period() === 1 ? 'Hoy' : `Últimos ${this.period()} días`,
  );

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      const days = this.period();
      this.reloadVersion();
      if (!slug) return;

      this.loading.set(true);
      this.errorMessage.set('');
      const subscription = this.analytics.summary(slug, days).subscribe({
        next: (summary) => {
          this.summary.set(summary);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.errorMessage.set('Intentá nuevamente en unos segundos.');
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  reload(): void {
    this.reloadVersion.update((value) => value + 1);
  }

  number(value: number): string {
    return new Intl.NumberFormat('es-AR').format(value);
  }

  percent(value: number): string {
    return `${new Intl.NumberFormat('es-AR', { minimumFractionDigits: 1, maximumFractionDigits: 2 }).format(value)}%`;
  }

  ratio(value: number, total: number): string {
    if (total <= 0) return '0%';
    return this.percent((value / total) * 100);
  }

  barWidth(value: number, maximum: number): number {
    if (value <= 0 || maximum <= 0) return 0;
    return Math.max(2, Math.min(100, (value / maximum) * 100));
  }
}
