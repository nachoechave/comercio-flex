import { HttpClient } from '@angular/common/http';
import { Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { switchMap, finalize } from 'rxjs';
import { inheritedRouteParam } from '../../core/routing/inherited-route-param';
import { CsrfService } from '../../core/auth/csrf.service';
import { ShippingSettings } from './shipping.models';
@Component({
  selector: 'app-shipping-settings',
  imports: [FormsModule],
  template: `
    <h1>Configuración de envíos</h1>
    <p>El envío gratis se calcula sobre los productos antes del descuento por transferencia.</p>
    @if (error()) {
      <p role="alert">{{ error() }}</p>
    }
    @if (settings; as s) {
      <form #form="ngForm" (ngSubmit)="form.valid && save()">
        <label
          >Envío gratis desde (vacío: deshabilitado)<input
            name="threshold"
            type="number"
            min="0"
            step="0.01"
            [(ngModel)]="s.freeShippingThreshold"
        /></label>
        @for (m of s.methods; track $index; let i = $index) {
          <fieldset>
            <legend>{{ m.name || 'Nuevo método' }}</legend>
            <label
              >Nombre<input [name]="'name' + i" required maxlength="160" [(ngModel)]="m.name"
            /></label>
            <label
              >Tipo<select [name]="'type' + i" [(ngModel)]="m.type">
                <option value="PICKUP">Retiro en local</option>
                <option value="FIXED_RATE">Tarifa fija</option>
                <option value="LOCATION_RATE">Por localidad</option>
                <option value="POSTAL_CODE_RATE">Por código postal</option>
              </select></label
            >
            <label
              ><input type="checkbox" [name]="'active' + i" [(ngModel)]="m.active" />Activo</label
            >
            <label
              >Descripción<input
                [name]="'description' + i"
                maxlength="1000"
                [(ngModel)]="m.description"
            /></label>
            @if (m.type === 'PICKUP') {
              <label
                >Dirección de retiro<input
                  [name]="'address' + i"
                  required
                  maxlength="500"
                  [(ngModel)]="m.pickupAddress"
              /></label>
              <label
                >Instrucciones<input
                  [name]="'instructions' + i"
                  maxlength="1000"
                  [(ngModel)]="m.instructions"
              /></label>
            }
            @if (m.type === 'PICKUP' || m.type === 'FIXED_RATE') {
              <label
                >Precio<input
                  [name]="'price' + i"
                  required
                  type="number"
                  min="0"
                  step="0.01"
                  [(ngModel)]="m.price"
              /></label>
            } @else {
              @for (r of m.rules; track $index; let j = $index) {
                <div class="rule">
                  <label
                    >{{ m.type === 'LOCATION_RATE' ? 'Localidad' : 'Código postal'
                    }}<input
                      [name]="'destination' + i + '-' + j"
                      required
                      maxlength="160"
                      [(ngModel)]="r.destination"
                  /></label>
                  <label
                    >Precio<input
                      [name]="'rate' + i + '-' + j"
                      required
                      type="number"
                      min="0"
                      step="0.01"
                      [(ngModel)]="r.price"
                  /></label>
                  <button type="button" (click)="m.rules.splice(j, 1)">Eliminar regla</button>
                </div>
              }
              <button type="button" (click)="m.rules.push({ destination: '', price: 0 })">
                Agregar regla
              </button>
            }
            <button type="button" (click)="s.methods.splice(i, 1)">Eliminar método</button>
          </fieldset>
        }
        <button type="button" (click)="add()">Agregar método</button>
        <button type="submit" [disabled]="saving() || form.invalid">Guardar envíos</button>
      </form>
    }
    @if (notice()) {
      <p role="status">{{ notice() }}</p>
    }
  `,
  styles: [
    `
      form,
      fieldset {
        display: grid;
        gap: 16px;
      }
      fieldset {
        border: 1px solid #d8dee5;
        border-radius: 12px;
        padding: 20px;
      }
      label {
        display: grid;
        gap: 6px;
      }
      input,
      select,
      button {
        padding: 10px;
        max-width: 100%;
        box-sizing: border-box;
      }
      .rule {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
      }
      button {
        cursor: pointer;
      }
      h1 {
        margin-top: 0;
      }
    `,
  ],
})
export class ShippingSettingsPage {
  private http = inject(HttpClient);
  private csrf = inject(CsrfService);
  slug = toSignal(inheritedRouteParam(inject(ActivatedRoute), 'storeSlug'), { initialValue: '' });
  settings: ShippingSettings | null = null;
  error = signal('');
  notice = signal('');
  saving = signal(false);
  constructor() {
    effect((onCleanup) => {
      const slug = this.slug();
      if (!slug) return;
      const sub = this.http
        .get<ShippingSettings>(this.url(slug))
        .subscribe({
          next: (s) => (this.settings = s),
          error: () => this.error.set('No pudimos cargar envíos.'),
        });
      onCleanup(() => sub.unsubscribe());
    });
  }
  private url(slug = this.slug()) {
    return '/api/v1/stores/' + encodeURIComponent(slug ?? '') + '/admin/shipping';
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
  save() {
    if (this.saving()) return;
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
        next: (s) => {
          this.settings = s;
          this.notice.set('Configuración guardada.');
        },
        error: (e) => this.error.set(e.error?.message ?? 'No pudimos guardar envíos.'),
      });
  }
}
