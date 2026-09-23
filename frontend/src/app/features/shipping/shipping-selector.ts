import { HttpClient } from '@angular/common/http';
import { Component, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ShippingQuote, ShippingSelection } from './shipping.models';
import { StorefrontMoneyPipe } from '../storefront/storefront-money.pipe';
@Component({
  selector: 'app-shipping-selector',
  imports: [ReactiveFormsModule, StorefrontMoneyPipe],
  template: ` <fieldset>
    <legend>Método de entrega</legend>
    <label
      ><input
        type="radio"
        name="fulfillment"
        [checked]="mode() === 'PICKUP'"
        (change)="setMode('PICKUP')"
        [disabled]="disabled()"
      />
      Retiro en local</label
    >
    <label
      ><input
        type="radio"
        name="fulfillment"
        [checked]="mode() === 'SHIPPING'"
        (change)="setMode('SHIPPING')"
        [disabled]="disabled()"
      />
      Envío a domicilio</label
    >
    @if (mode() === 'SHIPPING') {
      <div [formGroup]="address" class="address">
        @for (field of fields; track field.key) {
          <label
            >{{ field.label
            }}<input
              [formControlName]="field.key"
              [attr.data-address-field]="field.key"
              [readonly]="disabled()"
              [attr.autocomplete]="field.autocomplete"
              [attr.maxlength]="field.max"
              (input)="invalidate()"
            />
            @if (address.controls[field.key].touched && address.controls[field.key].invalid) {
              <small>Completá este campo.</small>
            }
          </label>
        }
      </div>
    }
    <button
      type="button"
      (click)="quote()"
      [disabled]="loading() || disabled() || !paymentMethod()"
    >
      Consultar opciones de entrega
    </button>
    @if (loading()) {
      <p role="status">Calculando envío…</p>
    }
    @if (error()) {
      <p role="alert">{{ error() }}</p>
    }
    @for (option of visibleOptions(); track option.methodId) {
      <label class="option"
        ><input
          type="radio"
          name="shippingMethod"
          [checked]="selected() === option.methodId"
          (change)="select(option)"
          [disabled]="disabled()"
        />
        <span
          ><strong>{{ option.name }}</strong> ·
          {{ option.shippingAmount | storefrontMoney: currency() }}
          @if (option.freeShipping) {
            <span>Envío gratis</span>
          }
          <span>{{ option.description }}</span
          ><span>{{ option.pickupAddress }}</span
          ><span>{{ option.instructions }}</span></span
        ></label
      >
    }
    @if (current(); as q) {
      <dl>
        <dt>Productos</dt>
        <dd>{{ q.listSubtotal | storefrontMoney: currency() }}</dd>
        <dt>Descuento</dt>
        <dd>{{ q.discountAmount | storefrontMoney: currency() }}</dd>
        <dt>Envío</dt>
        <dd>{{ q.shippingAmount | storefrontMoney: currency() }}</dd>
        <dt>Total</dt>
        <dd>
          <strong>{{ q.total | storefrontMoney: currency() }}</strong>
        </dd>
      </dl>
    }
  </fieldset>`,
  styles: [
    `
      fieldset {
        border: 1px solid #d8dee5;
        border-radius: 12px;
        padding: 16px;
        display: grid;
        gap: 16px;
        min-width: 0;
      }
      label {
        display: flex;
        gap: 8px;
        align-items: start;
      }
      .address {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(min(100%, 200px), 1fr));
        gap: 12px;
      }
      .address label {
        display: grid;
      }
      input:not([type='radio']) {
        width: 100%;
        box-sizing: border-box;
        padding: 10px;
        border: 1px solid #a8b0ba;
        border-radius: 6px;
      }
      button {
        padding: 12px;
        cursor: pointer;
      }
      .option span span {
        display: block;
      }
      dl {
        display: grid;
        grid-template-columns: 1fr auto;
      }
      dd {
        margin: 0;
      }
      small,
      [role='alert'] {
        color: #a21c24;
      }
    `,
  ],
})
export class ShippingSelector {
  private readonly http = inject(HttpClient);
  storeSlug = input.required<string>();
  items = input.required<{ variantId: string; quantity: string }[]>();
  paymentMethod = input<string | null>(null);
  currency = input('ARS');
  disabled = input(false);
  selection = output<ShippingSelection | null>();
  quoted = output<ShippingQuote | null>();
  mode = signal<'PICKUP' | 'SHIPPING'>('PICKUP');
  loading = signal(false);
  error = signal('');
  options = signal<ShippingQuote[]>([]);
  selected = signal<string | null>(null);
  current = signal<ShippingQuote | null>(null);
  private generation = 0;
  address = inject(FormBuilder).nonNullable.group({
    street: ['', Validators.required],
    number: ['', Validators.required],
    apartment: [''],
    city: ['', Validators.required],
    province: ['', Validators.required],
    postalCode: ['', Validators.required],
  });
  fields = [
    { key: 'street' as const, label: 'Calle', autocomplete: 'address-line1', max: 160 },
    { key: 'number' as const, label: 'Número', autocomplete: 'off', max: 30 },
    {
      key: 'apartment' as const,
      label: 'Piso/departamento (opcional)',
      autocomplete: 'address-line2',
      max: 80,
    },
    { key: 'city' as const, label: 'Localidad', autocomplete: 'address-level2', max: 160 },
    { key: 'province' as const, label: 'Provincia', autocomplete: 'address-level1', max: 160 },
    { key: 'postalCode' as const, label: 'Código postal', autocomplete: 'postal-code', max: 20 },
  ];
  constructor() {
    effect(() => {
      this.storeSlug();
      this.items();
      this.paymentMethod();
      this.refreshVersion();
      this.invalidate();
    });
  }
  refreshVersion = input(0);
  visibleOptions() {
    return this.options().filter((q) => (q.type === 'PICKUP') === (this.mode() === 'PICKUP'));
  }
  setMode(mode: 'PICKUP' | 'SHIPPING') {
    this.mode.set(mode);
    this.invalidate();
  }
  invalidate() {
    this.generation++;
    this.loading.set(false);
    this.options.set([]);
    this.selected.set(null);
    this.current.set(null);
    this.selection.emit(null);
    this.quoted.emit(null);
  }
  quote() {
    this.address.markAllAsTouched();
    if (this.mode() === 'SHIPPING' && this.address.invalid) {
      this.error.set('Completá la dirección de entrega.');
      return;
    }
    this.invalidate();
    this.error.set('');
    this.loading.set(true);
    const generation = this.generation;
    const a = this.address.getRawValue();
    this.http
      .post<ShippingQuote[]>(
        '/api/v1/stores/' + encodeURIComponent(this.storeSlug()) + '/shipping/quote',
        {
          items: this.items(),
          paymentMethod: this.paymentMethod(),
          city: a.city,
          postalCode: a.postalCode,
        },
      )
      .subscribe({
        next: (options) => {
          if (generation !== this.generation) return;
          this.loading.set(false);
          this.options.set(options);
          if (!this.visibleOptions().length)
            this.error.set('No hay métodos disponibles para este destino.');
        },
        error: () => {
          if (generation !== this.generation) return;
          this.loading.set(false);
          this.error.set('No pudimos calcular el envío. Reintentá.');
        },
      });
  }
  select(q: ShippingQuote) {
    this.selected.set(q.methodId);
    this.current.set(q);
    this.quoted.emit(q);
    this.selection.emit({
      methodId: q.methodId,
      expectedTotal: q.total,
      ...(q.type === 'PICKUP' ? {} : { address: this.address.getRawValue() }),
    });
  }
}
