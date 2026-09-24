import { HttpClient } from '@angular/common/http';
import { Component, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, switchMap } from 'rxjs';
import { CsrfService } from '../../core/auth/csrf.service';
import { Shipment, ShippingStatus } from './shipping.models';
@Component({
  selector: 'app-shipment-panel',
  imports: [FormsModule],
  template: `
    <h2>Envío y seguimiento</h2>
    @if (error()) {
      <p role="alert">{{ error() }}</p>
    }
    @if (shipment; as s) {
      <form #form="ngForm" (ngSubmit)="form.valid && save()">
        <label
          >Estado<select name="status" [(ngModel)]="s.status">
            @for (status of allowed(); track status) {
              <option [value]="status">{{ labels[status] }}</option>
            }
          </select></label
        >
        <label
          >Empresa de transporte<input name="carrier" maxlength="160" [(ngModel)]="s.carrierName"
        /></label>
        <label
          >Número de seguimiento<input
            name="tracking"
            maxlength="160"
            [(ngModel)]="s.trackingNumber"
        /></label>
        <label
          >URL de seguimiento<input
            name="url"
            type="url"
            maxlength="1000"
            [(ngModel)]="s.trackingUrl"
        /></label>
        <label
          >Notas internas<textarea name="notes" maxlength="1000" [(ngModel)]="s.notes"></textarea>
        </label>
        <button [disabled]="saving()">Guardar envío</button>
      </form>
    }
    @if (notice()) {
      <p role="status">{{ notice() }}</p>
    }
  `,
  styles: [
    `
      form {
        display: grid;
        gap: 12px;
      }
      label {
        display: grid;
        gap: 4px;
      }
      input,
      select,
      textarea,
      button {
        padding: 10px;
        max-width: 100%;
        box-sizing: border-box;
      }
    `,
  ],
})
export class ShipmentPanel {
  changed = output<Shipment>();
  private http = inject(HttpClient);
  private csrf = inject(CsrfService);
  storeSlug = input.required<string>();
  orderId = input.required<string>();
  shipment: Shipment | null = null;
  original = signal<ShippingStatus>('PENDING');
  error = signal('');
  notice = signal('');
  saving = signal(false);
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
        next: (s) => {
          this.shipment = s;
          if (s) {
            this.original.set(s.status);
            this.changed.emit(s);
          }
        },
        error: () => this.error.set('No pudimos cargar el envío.'),
      });
      onCleanup(() => sub.unsubscribe());
    });
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
  allowed(): ShippingStatus[] {
    const s = this.original();
    return s === 'PENDING'
      ? [s, 'PREPARING', 'CANCELLED']
      : s === 'PREPARING'
        ? [s, 'SHIPPED', 'CANCELLED']
        : s === 'SHIPPED'
          ? [s, 'DELIVERED']
          : [s];
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
        next: (s) => {
          this.shipment = s;
          this.original.set(s.status);
          this.changed.emit(s);
          this.notice.set('Envío actualizado.');
        },
        error: (e) => this.error.set(e.error?.message ?? 'No pudimos actualizar el envío.'),
      });
  }
}
