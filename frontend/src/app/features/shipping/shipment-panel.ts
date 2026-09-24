import { HttpClient } from '@angular/common/http';
import { ChangeDetectorRef, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, switchMap } from 'rxjs';
import { CsrfService } from '../../core/auth/csrf.service';
import { Shipment, ShippingStatus } from './shipping.models';

@Component({
  selector: 'app-shipment-panel',
  imports: [FormsModule],
  template: `
    <section class="shipment-panel">
      <header>
        <div>
          <h2>Envío y seguimiento</h2>
          @if (shipment?.provider) {
            <span class="provider">{{ shipment?.provider }}</span>
          }
        </div>
        @if (shipment?.providerStatus) {
          <small>Estado transportista: {{ shipment?.providerStatus }}</small>
        }
      </header>

      @if (error()) {
        <p class="notice error" role="alert">{{ error() }}</p>
      }
      @if (notice()) {
        <p class="notice success" role="status">{{ notice() }}</p>
      }

      @if (shipment; as s) {
        @if (s.provider === 'ANDREANI') {
          <section class="carrier-box">
            <div>
              <strong>Andreani</strong>
              @if (s.externalReference) {
                <span>Envío {{ s.externalReference }}</span>
              } @else {
                <span>El pedido todavía no fue generado en Andreani.</span>
              }
              @if (s.lastSyncedAt) {
                <small>Última actualización: {{ s.lastSyncedAt }}</small>
              }
            </div>
            <div class="carrier-actions">
              @if (!s.externalReference) {
                <button type="button" (click)="provision()" [disabled]="provisioning()">
                  {{ provisioning() ? 'Generando…' : 'Generar envío en Andreani' }}
                </button>
              }
              @if (s.labelReference) {
                <a [href]="labelUrl()" target="_blank" rel="noopener">Descargar etiqueta PDF</a>
              }
            </div>
          </section>
          @if (s.providerError) {
            <p class="notice warning">Andreani: {{ s.providerError }}</p>
          }
        }

        <form #form="ngForm" (ngSubmit)="form.valid && save()">
          <label>
            Estado
            <select name="status" [(ngModel)]="s.status">
              @for (status of allowed(); track status) {
                <option [value]="status">{{ labels[status] }}</option>
              }
            </select>
          </label>
          <label>
            Empresa de transporte
            <input name="carrier" maxlength="160" [(ngModel)]="s.carrierName" />
          </label>
          <label>
            Número de seguimiento
            <input name="tracking" maxlength="160" [(ngModel)]="s.trackingNumber" />
          </label>
          <label>
            URL de seguimiento
            <input name="url" type="url" maxlength="1000" [(ngModel)]="s.trackingUrl" />
          </label>
          <label>
            Notas internas
            <textarea name="notes" maxlength="1000" [(ngModel)]="s.notes"></textarea>
          </label>
          <button class="save" [disabled]="saving()">{{ saving() ? 'Guardando…' : 'Guardar envío' }}</button>
        </form>
      }
    </section>
  `,
  styles: [
    `
      .shipment-panel { display: grid; gap: 14px; }
      header { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
      header > div { display: flex; gap: 9px; align-items: center; }
      h2 { margin: 0; }
      .provider { padding: 3px 8px; border-radius: 999px; background: #eef2ff; color: #3949ab; font-size: .75rem; font-weight: 800; }
      .carrier-box { display: flex; justify-content: space-between; gap: 16px; align-items: center; padding: 14px; border: 1px solid #dfe4ea; border-radius: 12px; background: #f8fafc; }
      .carrier-box > div:first-child { display: grid; gap: 3px; }
      .carrier-actions { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
      .carrier-actions button, .carrier-actions a, .save { border: 0; border-radius: 9px; padding: 10px 13px; background: #172033; color: #fff; font: inherit; font-weight: 750; text-decoration: none; cursor: pointer; }
      .carrier-actions a { background: #fff; color: #172033; border: 1px solid #cfd7e3; }
      form { display: grid; gap: 12px; }
      label { display: grid; gap: 5px; color: #344054; font-size: .9rem; font-weight: 650; }
      input, select, textarea { padding: 10px; max-width: 100%; box-sizing: border-box; border: 1px solid #cfd7e3; border-radius: 9px; font: inherit; }
      textarea { min-height: 82px; resize: vertical; }
      .save { justify-self: start; }
      .notice { margin: 0; padding: 10px 12px; border-radius: 9px; }
      .notice.error { background: #fff1f2; color: #a21c24; }
      .notice.success { background: #e8f7ee; color: #18794e; }
      .notice.warning { background: #fff8e6; color: #8a5a00; }
      @media (max-width: 640px) { header, .carrier-box { display: grid; } }
    `,
  ],
})
export class ShipmentPanel {
  changed = output<Shipment>();
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);
  private readonly changeDetector = inject(ChangeDetectorRef);

  storeSlug = input.required<string>();
  orderId = input.required<string>();
  shipment: Shipment | null = null;
  original = signal<ShippingStatus>('PENDING');
  error = signal('');
  notice = signal('');
  saving = signal(false);
  provisioning = signal(false);
  labels: Record<ShippingStatus, string> = {
    PENDING: 'Pendiente',
    PREPARING: 'Preparando',
    SHIPPED: 'Despachado',
    DELIVERED: 'Entregado',
    CANCELLED: 'Cancelado',
  };

  constructor() {
    effect((onCleanup) => {
      const url = this.url();
      const sub = this.http.get<Shipment | null>(url).subscribe({
        next: (shipment) => this.accept(shipment),
        error: () => this.error.set('No pudimos cargar el envío.'),
      });
      onCleanup(() => sub.unsubscribe());
    });
  }

  private accept(shipment: Shipment | null) {
    this.shipment = shipment;
    if (shipment) {
      this.original.set(shipment.status);
      this.changed.emit(shipment);
    }
    this.changeDetector.markForCheck();
  }

  private url() {
    return (
      '/api/v1/stores/' +
      encodeURIComponent(this.storeSlug()) +
      '/admin/orders/' +
      encodeURIComponent(this.orderId()) +
      '/shipment'
    );
  }

  labelUrl() {
    return this.url() + '/label';
  }

  allowed(): ShippingStatus[] {
    const status = this.original();
    return status === 'PENDING'
      ? [status, 'PREPARING', 'CANCELLED']
      : status === 'PREPARING'
        ? [status, 'SHIPPED', 'CANCELLED']
        : status === 'SHIPPED'
          ? [status, 'DELIVERED']
          : [status];
  }

  provision() {
    if (this.provisioning()) return;
    this.provisioning.set(true);
    this.error.set('');
    this.notice.set('');
    this.csrf
      .ensureToken()
      .pipe(
        switchMap(() => this.http.post<Shipment>(this.url() + '/carrier', {})),
        finalize(() => this.provisioning.set(false)),
      )
      .subscribe({
        next: (shipment) => {
          this.accept(shipment);
          this.notice.set('Envío generado en Andreani. Ya podés descargar la etiqueta.');
        },
        error: (response) =>
          this.error.set(response.error?.message ?? 'No pudimos generar el envío en Andreani.'),
      });
  }

  save() {
    if (this.saving() || !this.shipment) return;
    this.saving.set(true);
    this.error.set('');
    this.notice.set('');
    this.csrf
      .ensureToken()
      .pipe(
        switchMap(() => this.http.put<Shipment>(this.url(), this.shipment)),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: (shipment) => {
          this.accept(shipment);
          this.notice.set('Envío actualizado.');
        },
        error: (response) =>
          this.error.set(response.error?.message ?? 'No pudimos actualizar el envío.'),
      });
  }
}
