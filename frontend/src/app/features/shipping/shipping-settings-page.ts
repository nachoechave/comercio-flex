import { HttpClient } from '@angular/common/http';
import { ChangeDetectorRef, Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { finalize, switchMap } from 'rxjs';
import { CsrfService } from '../../core/auth/csrf.service';
import { inheritedRouteParam } from '../../core/routing/inherited-route-param';
import { ShippingMethod, ShippingSettings, ShippingType } from './shipping.models';

@Component({
  selector: 'app-shipping-settings',
  imports: [FormsModule],
  template: `
    <section class="shipping-page">
      <header class="page-header">
        <div>
          <span class="eyebrow">LOGÍSTICA</span>
          <h1>Envíos y retiros</h1>
          <p>
            Definí cómo reciben los pedidos tus clientes. Podés combinar retiro en local, tarifa
            fija y precios por localidad, provincia, resto del país o código postal.
          </p>
        </div>
        @if (settings; as s) {
          <div class="summary-card">
            <span>Métodos activos</span>
            <strong>{{ activeCount(s) }}</strong>
            <small>de {{ s.methods.length }} configurados</small>
          </div>
        }
      </header>

      @if (error()) {
        <div class="notice error" role="alert">{{ error() }}</div>
      }
      @if (notice()) {
        <div class="notice success" role="status">{{ notice() }}</div>
      }

      @if (loading()) {
        <section class="loading-card" role="status">
          <strong>Cargando configuración de envíos…</strong>
          <span>Estamos consultando los métodos disponibles para este comercio.</span>
        </section>
      } @else if (settings; as s) {
        <form #form="ngForm" (ngSubmit)="form.valid && save()">
          <section class="panel free-shipping-panel">
            <div>
              <span class="panel-kicker">BENEFICIO</span>
              <h2>Envío gratis</h2>
              <p>
                Si configurás un monto, el costo de entrega pasa a $0 cuando los productos alcanzan
                ese subtotal. Se calcula antes del descuento por transferencia.
              </p>
            </div>
            <label class="money-field">
              <span>Gratis desde</span>
              <div class="money-input">
                <span>$</span>
                <input
                  name="threshold"
                  type="number"
                  min="0"
                  step="0.01"
                  placeholder="Ej. 50000"
                  [(ngModel)]="s.freeShippingThreshold"
                />
              </div>
              <small>Dejalo vacío para deshabilitar el envío gratis.</small>
            </label>
          </section>

          <div class="section-heading">
            <div>
              <span class="panel-kicker">MÉTODOS DE ENTREGA</span>
              <h2>Opciones disponibles</h2>
              <p>El cliente solo verá los métodos activos que apliquen a su destino.</p>
            </div>
            <button class="secondary-button" type="button" (click)="add()">+ Agregar método</button>
          </div>

          @if (!s.methods.length) {
            <section class="empty-state">
              <strong>No hay métodos configurados.</strong>
              <p>Agregá retiro en local o una opción de envío para que el checkout pueda cotizar.</p>
              <button class="primary-button" type="button" (click)="add()">Crear primer método</button>
            </section>
          }

          <div class="methods-grid">
            @for (m of s.methods; track m.id ?? $index; let i = $index) {
              <section class="method-card" [class.inactive]="!m.active">
                <header class="method-header">
                  <div>
                    <span class="type-badge">{{ methodTypeLabel(m.type) }}</span>
                    <h3>{{ m.name || 'Nuevo método' }}</h3>
                  </div>
                  <label class="switch-label">
                    <input type="checkbox" [name]="'active' + i" [(ngModel)]="m.active" />
                    <span>{{ m.active ? 'Activo' : 'Inactivo' }}</span>
                  </label>
                </header>

                <div class="form-grid">
                  <label class="full-width">
                    <span>Nombre que verá el cliente</span>
                    <input
                      [name]="'name' + i"
                      required
                      maxlength="160"
                      placeholder="Ej. Envío estándar"
                      [(ngModel)]="m.name"
                    />
                  </label>

                  <label>
                    <span>Tipo</span>
                    <select [name]="'type' + i" [(ngModel)]="m.type">
                      <option value="PICKUP">Retiro en local</option>
                      <option value="FIXED_RATE">Tarifa fija</option>
                      <option value="LOCATION_RATE">Por destino</option>
                      <option value="POSTAL_CODE_RATE">Por código postal</option>
                    </select>
                  </label>

                  @if (m.type === 'PICKUP' || m.type === 'FIXED_RATE') {
                    <label>
                      <span>{{ m.type === 'PICKUP' ? 'Costo de retiro' : 'Costo del envío' }}</span>
                      <div class="money-input compact">
                        <span>$</span>
                        <input
                          [name]="'price' + i"
                          required
                          type="number"
                          min="0"
                          step="0.01"
                          [(ngModel)]="m.price"
                        />
                      </div>
                    </label>
                  }

                  <label class="full-width">
                    <span>Descripción</span>
                    <input
                      [name]="'description' + i"
                      maxlength="1000"
                      placeholder="Ej. Entrega de lunes a viernes"
                      [(ngModel)]="m.description"
                    />
                  </label>

                  @if (m.type === 'PICKUP') {
                    <label class="full-width">
                      <span>Dirección de retiro</span>
                      <input
                        [name]="'address' + i"
                        required
                        maxlength="500"
                        placeholder="Calle, número y localidad"
                        [(ngModel)]="m.pickupAddress"
                      />
                    </label>
                    <label class="full-width">
                      <span>Instrucciones para el cliente</span>
                      <textarea
                        [name]="'instructions' + i"
                        maxlength="1000"
                        rows="3"
                        placeholder="Ej. Retirar de 9 a 18 h presentando número de pedido"
                        [(ngModel)]="m.instructions"
                      ></textarea>
                    </label>
                  }
                </div>

                @if (m.type === 'LOCATION_RATE' || m.type === 'POSTAL_CODE_RATE') {
                  <div class="rules-section">
                    <div class="rules-heading">
                      <div>
                        <strong>Tarifas por destino</strong>
                        <span>
                          {{
                            m.type === 'LOCATION_RATE'
                              ? 'Combiná localidades, provincias y una tarifa para el resto de Argentina.'
                              : 'Agregá cada código postal con su precio.'
                          }}
                        </span>
                      </div>
                      <button class="text-button" type="button" (click)="addRule(m)">
                        + Agregar tarifa
                      </button>
                    </div>

                    @if (!m.rules.length) {
                      <p class="rules-empty">Todavía no agregaste destinos para este método.</p>
                    }

                    @for (r of m.rules; track $index; let j = $index) {
                      <div class="rule-row" [class.rule-row-location]="m.type === 'LOCATION_RATE'">
                        @if (m.type === 'LOCATION_RATE') {
                          <label>
                            <span>Alcance</span>
                            <select
                              [name]="'scope' + i + '-' + j"
                              [ngModel]="ruleScope(r.destination)"
                              (ngModelChange)="setRuleScope(r, $event)"
                            >
                              <option value="LOCALITY">Localidad específica</option>
                              <option value="PROVINCE">Provincia completa</option>
                              <option value="COUNTRY_REST">Resto de Argentina</option>
                            </select>
                          </label>
                          @if (ruleScope(r.destination) !== 'COUNTRY_REST') {
                            <label>
                              <span>{{ ruleScope(r.destination) === 'PROVINCE' ? 'Provincia' : 'Localidad' }}</span>
                              <input
                                [name]="'destination' + i + '-' + j"
                                required
                                maxlength="160"
                                [ngModel]="ruleValue(r.destination)"
                                (ngModelChange)="setRuleValue(r, $event)"
                                [placeholder]="ruleScope(r.destination) === 'PROVINCE' ? 'Ej. San Juan' : 'Ej. Rawson'"
                              />
                            </label>
                          } @else {
                            <div class="country-rest-copy">
                              <strong>Todo el país</strong>
                              <span>Se usa si no coincide una localidad o provincia anterior.</span>
                            </div>
                          }
                        } @else {
                          <label>
                            <span>Código postal</span>
                            <input
                              [name]="'destination' + i + '-' + j"
                              required
                              maxlength="160"
                              [(ngModel)]="r.destination"
                            />
                          </label>
                        }
                        <label>
                          <span>Precio</span>
                          <div class="money-input compact">
                            <span>$</span>
                            <input
                              [name]="'rate' + i + '-' + j"
                              required
                              type="number"
                              min="0"
                              step="0.01"
                              [(ngModel)]="r.price"
                            />
                          </div>
                        </label>
                        <button
                          class="icon-button danger"
                          type="button"
                          aria-label="Eliminar tarifa"
                          (click)="m.rules.splice(j, 1)"
                        >
                          ×
                        </button>
                      </div>
                    }
                  </div>
                }

                <footer class="method-footer">
                  <span>{{ methodHint(m.type) }}</span>
                  <button class="danger-button" type="button" (click)="s.methods.splice(i, 1)">
                    Eliminar método
                  </button>
                </footer>
              </section>
            }
          </div>

          <footer class="save-bar">
            <div>
              <strong>Guardá los cambios antes de salir.</strong>
              <span>La próxima cotización del checkout usará esta configuración.</span>
            </div>
            <button class="primary-button" type="submit" [disabled]="saving() || form.invalid">
              {{ saving() ? 'Guardando…' : 'Guardar envíos' }}
            </button>
          </footer>
        </form>
      }
    </section>
  `,
  styles: [
    `
      :host { display: block; }
      .shipping-page { display: grid; gap: 24px; max-width: 1120px; margin: 0 auto; padding-bottom: 32px; }
      .page-header { display: flex; justify-content: space-between; gap: 24px; align-items: flex-start; padding: 28px; border: 1px solid #e3e8ef; border-radius: 20px; background: linear-gradient(135deg, #fff, #f7f9fc); }
      .page-header h1, .section-heading h2, .panel h2, .method-card h3 { margin: 0; color: #172033; }
      .page-header h1 { margin-top: 4px; font-size: clamp(1.75rem, 3vw, 2.35rem); }
      .page-header p, .section-heading p, .panel p, .empty-state p { margin: 8px 0 0; color: #667085; line-height: 1.55; }
      .eyebrow, .panel-kicker { font-size: .72rem; font-weight: 800; letter-spacing: .12em; color: #5b6b7f; }
      .summary-card { min-width: 150px; display: grid; gap: 2px; padding: 16px 18px; border-radius: 14px; background: #172033; color: #fff; }
      .summary-card span, .summary-card small { color: #cbd5e1; }
      .summary-card strong { font-size: 1.8rem; }
      form { display: grid; gap: 24px; }
      .panel, .method-card, .empty-state, .loading-card { border: 1px solid #e3e8ef; border-radius: 18px; background: #fff; }
      .loading-card { display: grid; gap: 6px; padding: 24px; color: #172033; }
      .loading-card span { color: #667085; }
      .free-shipping-panel { display: grid; grid-template-columns: minmax(0, 1fr) minmax(240px, 340px); gap: 28px; align-items: center; padding: 24px; }
      .money-field, .form-grid label, .rule-row label { display: grid; gap: 7px; color: #344054; font-size: .88rem; font-weight: 650; }
      .money-field small, .rules-empty, .method-footer > span, .save-bar span { color: #667085; font-size: .86rem; font-weight: 400; }
      input, select, textarea { width: 100%; box-sizing: border-box; border: 1px solid #cfd7e3; border-radius: 10px; padding: 11px 12px; background: #fff; color: #172033; font: inherit; outline: none; }
      input:focus, select:focus, textarea:focus { border-color: #5b6b7f; box-shadow: 0 0 0 3px rgb(91 107 127 / 12%); }
      textarea { resize: vertical; }
      .money-input { display: flex; align-items: center; border: 1px solid #cfd7e3; border-radius: 10px; background: #fff; overflow: hidden; }
      .money-input > span { padding-left: 12px; color: #667085; font-weight: 700; }
      .money-input input { border: 0; box-shadow: none; }
      .section-heading { display: flex; justify-content: space-between; gap: 20px; align-items: flex-end; }
      .methods-grid { display: grid; gap: 18px; }
      .method-card { overflow: hidden; }
      .method-card.inactive { opacity: .72; }
      .method-header { display: flex; justify-content: space-between; gap: 16px; align-items: center; padding: 18px 20px; border-bottom: 1px solid #edf0f4; background: #fbfcfe; }
      .method-header h3 { margin-top: 5px; font-size: 1.08rem; }
      .type-badge { display: inline-flex; padding: 4px 8px; border-radius: 999px; background: #edf2f7; color: #475467; font-size: .7rem; font-weight: 800; letter-spacing: .04em; }
      .switch-label { display: flex; gap: 8px; align-items: center; color: #344054; font-size: .88rem; font-weight: 700; }
      .switch-label input { width: auto; }
      .form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; padding: 20px; }
      .full-width { grid-column: 1 / -1; }
      .rules-section { margin: 0 20px 20px; padding: 16px; border-radius: 14px; background: #f7f9fc; }
      .rules-heading { display: flex; justify-content: space-between; gap: 16px; align-items: center; margin-bottom: 12px; }
      .rules-heading > div { display: grid; gap: 3px; }
      .rule-row { display: grid; grid-template-columns: minmax(0, 1fr) minmax(160px, 220px) auto; gap: 12px; align-items: end; padding-top: 12px; }
      .rule-row-location { grid-template-columns: minmax(160px, .7fr) minmax(0, 1fr) minmax(160px, 220px) auto; }
      .country-rest-copy { align-self: stretch; display: grid; align-content: center; gap: 3px; min-height: 42px; padding: 0 12px; border: 1px dashed #cfd7e3; border-radius: 10px; color: #344054; }
      .country-rest-copy span { color: #667085; font-size: .82rem; }
      .method-footer { display: flex; justify-content: space-between; gap: 16px; align-items: center; padding: 14px 20px; border-top: 1px solid #edf0f4; }
      button { font: inherit; }
      .primary-button, .secondary-button, .danger-button, .text-button, .icon-button { cursor: pointer; }
      .primary-button, .secondary-button, .danger-button { border-radius: 10px; padding: 10px 14px; font-weight: 750; }
      .primary-button { border: 1px solid #172033; background: #172033; color: #fff; }
      .primary-button:disabled { opacity: .55; cursor: not-allowed; }
      .secondary-button { border: 1px solid #cfd7e3; background: #fff; color: #172033; }
      .danger-button { border: 1px solid #f2c7cb; background: #fff7f8; color: #a21c24; }
      .text-button { border: 0; background: transparent; color: #27364a; font-weight: 750; }
      .icon-button { width: 40px; height: 40px; border: 1px solid #f2c7cb; border-radius: 9px; background: #fff; color: #a21c24; font-size: 1.25rem; }
      .empty-state { display: grid; justify-items: start; gap: 6px; padding: 28px; }
      .save-bar { position: sticky; bottom: 14px; display: flex; justify-content: space-between; gap: 18px; align-items: center; padding: 16px 18px; border: 1px solid #dfe5ed; border-radius: 14px; background: rgb(255 255 255 / 95%); box-shadow: 0 14px 36px rgb(16 24 40 / 12%); backdrop-filter: blur(10px); }
      .save-bar > div { display: grid; gap: 2px; }
      .notice { padding: 12px 14px; border-radius: 11px; font-weight: 650; }
      .notice.error { border: 1px solid #fecdd3; background: #fff1f2; color: #9f1239; }
      .notice.success { border: 1px solid #bbf7d0; background: #f0fdf4; color: #166534; }
      @media (max-width: 760px) {
        .page-header, .section-heading, .method-header, .method-footer, .save-bar { align-items: stretch; flex-direction: column; }
        .page-header { display: grid; }
        .summary-card { min-width: 0; }
        .free-shipping-panel, .form-grid, .rule-row { grid-template-columns: 1fr; }
        .full-width { grid-column: auto; }
        .rules-heading { align-items: flex-start; flex-direction: column; }
        .icon-button { width: 100%; }
        .save-bar { position: static; }
      }
    `,
  ],
})
export class ShippingSettingsPage {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);
  private readonly changeDetector = inject(ChangeDetectorRef);

  slug = toSignal(inheritedRouteParam(inject(ActivatedRoute), 'storeSlug'), { initialValue: '' });
  settings: ShippingSettings | null = null;
  loading = signal(true);
  error = signal('');
  notice = signal('');
  saving = signal(false);

  constructor() {
    effect((onCleanup) => {
      const slug = this.slug();
      if (!slug) return;

      this.loading.set(true);
      this.error.set('');
      const sub = this.http.get<ShippingSettings>(this.url(slug)).subscribe({
        next: (settings) => {
          this.settings = settings;
          this.loading.set(false);
          this.changeDetector.markForCheck();
        },
        error: () => {
          this.loading.set(false);
          this.error.set('No pudimos cargar envíos.');
        },
      });
      onCleanup(() => sub.unsubscribe());
    });
  }

  private url(slug = this.slug()) {
    return '/api/v1/stores/' + encodeURIComponent(slug ?? '') + '/admin/shipping';
  }

  activeCount(settings: ShippingSettings) {
    return settings.methods.filter((method) => method.active).length;
  }

  methodTypeLabel(type: ShippingType) {
    return (
      {
        PICKUP: 'RETIRO',
        FIXED_RATE: 'TARIFA FIJA',
        LOCATION_RATE: 'POR DESTINO',
        POSTAL_CODE_RATE: 'POR CÓDIGO POSTAL',
      } satisfies Record<ShippingType, string>
    )[type];
  }

  methodHint(type: ShippingType) {
    return (
      {
        PICKUP: 'No requiere dirección de entrega del comprador.',
        FIXED_RATE: 'Aplica el mismo costo a cualquier destino.',
        LOCATION_RATE: 'Prioridad: localidad específica, provincia y luego resto de Argentina.',
        POSTAL_CODE_RATE: 'Solo se ofrece cuando el código postal coincide con una tarifa.',
      } satisfies Record<ShippingType, string>
    )[type];
  }

  add() {
    this.settings?.methods.push({
      id: null,
      name: '',
      type: 'FIXED_RATE',
      price: 0,
      active: true,
      description: null,
      pickupAddress: null,
      instructions: null,
      rules: [],
    });
  }

  addRule(method: ShippingMethod) {
    method.rules.push({ destination: '', price: 0 });
  }

  ruleScope(destination: string): 'LOCALITY' | 'PROVINCE' | 'COUNTRY_REST' {
    if (destination === 'RESTO_ARGENTINA') return 'COUNTRY_REST';
    if (destination.startsWith('PROVINCIA:')) return 'PROVINCE';
    return 'LOCALITY';
  }

  ruleValue(destination: string): string {
    return destination.startsWith('PROVINCIA:')
      ? destination.slice('PROVINCIA:'.length)
      : destination === 'RESTO_ARGENTINA'
        ? ''
        : destination;
  }

  setRuleScope(
    rule: ShippingMethod['rules'][number],
    scope: 'LOCALITY' | 'PROVINCE' | 'COUNTRY_REST',
  ) {
    const value = this.ruleValue(rule.destination);
    rule.destination =
      scope === 'COUNTRY_REST'
        ? 'RESTO_ARGENTINA'
        : scope === 'PROVINCE'
          ? 'PROVINCIA:' + value
          : value;
  }

  setRuleValue(rule: ShippingMethod['rules'][number], value: string) {
    rule.destination =
      this.ruleScope(rule.destination) === 'PROVINCE' ? 'PROVINCIA:' + value : value;
  }

  save() {
    if (this.saving() || !this.settings) return;
    this.saving.set(true);
    this.error.set('');
    this.notice.set('');
    this.csrf
      .ensureToken()
      .pipe(
        switchMap(() => this.http.put<ShippingSettings>(this.url(), this.settings)),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: (settings) => {
          this.settings = settings;
          this.notice.set('Configuración guardada.');
          this.changeDetector.markForCheck();
        },
        error: (response) =>
          this.error.set(response.error?.message ?? 'No pudimos guardar envíos.'),
      });
  }
}
