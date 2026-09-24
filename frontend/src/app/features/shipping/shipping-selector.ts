import { HttpClient } from '@angular/common/http';
import { Component, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ShippingQuote, ShippingSelection } from './shipping.models';
import { StorefrontMoneyPipe } from '../storefront/storefront-money.pipe';

@Component({
  selector: 'app-shipping-selector',
  imports: [ReactiveFormsModule, StorefrontMoneyPipe],
  template: `
    <fieldset class="delivery-panel">
      <legend>Método de entrega</legend>
      <div class="mode-grid">
        <label class="mode-card" [class.selected]="mode() === 'PICKUP'">
          <input
            type="radio"
            name="fulfillment"
            [checked]="mode() === 'PICKUP'"
            (change)="setMode('PICKUP')"
            [disabled]="disabled()"
          />
          <span>
            <strong>Retiro en local</strong>
            <small>Coordiná el retiro directamente con el comercio.</small>
          </span>
        </label>
        <label class="mode-card" [class.selected]="mode() === 'SHIPPING'">
          <input
            type="radio"
            name="fulfillment"
            [checked]="mode() === 'SHIPPING'"
            (change)="setMode('SHIPPING')"
            [disabled]="disabled()"
          />
          <span>
            <strong>Envío a domicilio</strong>
            <small>Ingresá tu dirección para calcular las opciones disponibles.</small>
          </span>
        </label>
      </div>

      @if (mode() === 'SHIPPING') {
        <div [formGroup]="address" class="address">
          @for (field of fields; track field.key) {
            <label>
              <span>{{ field.label }}</span>
              <input
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
        class="quote-button"
        type="button"
        (click)="quote()"
        [disabled]="loading() || disabled() || !paymentMethod()"
      >
        {{ loading() ? 'Calculando…' : 'Consultar opciones de entrega' }}
      </button>

      @if (error()) {
        <p class="message error" role="alert">{{ error() }}</p>
      }

      @if (visibleOptions().length) {
        <div class="options">
          @for (option of visibleOptions(); track option.methodId) {
            <label class="option" [class.selected]="selected() === option.methodId">
              <input
                type="radio"
                name="shippingMethod"
                [checked]="selected() === option.methodId"
                (change)="select(option)"
                [disabled]="disabled()"
              />
              <span class="option-copy">
                <span class="option-heading">
                  <strong>{{ option.name }}</strong>
                  <strong>{{ option.shippingAmount | storefrontMoney: currency() }}</strong>
                </span>
                @if (option.freeShipping) {
                  <span class="free-badge">Envío gratis</span>
                }
                @if (option.description) {
                  <span>{{ option.description }}</span>
                }
                @if (option.pickupAddress) {
                  <span>{{ option.pickupAddress }}</span>
                }
                @if (option.instructions) {
                  <span>{{ option.instructions }}</span>
                }
              </span>
            </label>
          }
        </div>
      }

      @if (current(); as q) {
        <dl class="summary">
          <dt>Productos</dt>
          <dd>{{ q.listSubtotal | storefrontMoney: currency() }}</dd>
          @if (+q.discountAmount > 0) {
            <dt>Descuento</dt>
            <dd>-{{ q.discountAmount | storefrontMoney: currency() }}</dd>
          }
          <dt>Envío</dt>
          <dd>{{ q.shippingAmount | storefrontMoney: currency() }}</dd>
          <dt class="total">Total</dt>
          <dd class="total">{{ q.total | storefrontMoney: currency() }}</dd>
        </dl>
      }
    </fieldset>
  `,
  styles: [
    `
      .delivery-panel {
        border: 1px solid #dfe4ea;
        border-radius: 18px;
        padding: 20px;
        display: grid;
        gap: 18px;
        min-width: 0;
        background: #fff;
      }
      legend {
        padding: 0 8px;
        font-size: 1.05rem;
        font-weight: 700;
      }
      .mode-grid {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 12px;
      }
      .mode-card,
      .option {
        border: 1px solid #dfe4ea;
        border-radius: 14px;
        padding: 14px;
        transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
      }
      .mode-card {
        display: flex;
        gap: 10px;
        align-items: flex-start;
        cursor: pointer;
      }
      .mode-card.selected,
      .option.selected {
        border-color: #24364b;
        box-shadow: 0 0 0 2px rgb(36 54 75 / 8%);
        background: #f8fafc;
      }
      .mode-card span,
      .option-copy {
        display: grid;
        gap: 4px;
      }
      .mode-card small,
      .option-copy > span:not(.option-heading):not(.free-badge) {
        color: #667085;
      }
      .address {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(min(100%, 210px), 1fr));
        gap: 12px;
        padding: 16px;
        border-radius: 14px;
        background: #f8fafc;
      }
      .address label {
        display: grid;
        gap: 6px;
        font-size: 0.9rem;
        font-weight: 600;
      }
      input:not([type='radio']) {
        width: 100%;
        box-sizing: border-box;
        padding: 11px 12px;
        border: 1px solid #cfd6df;
        border-radius: 9px;
        background: #fff;
        font: inherit;
      }
      .quote-button {
        justify-self: start;
        border: 0;
        border-radius: 10px;
        padding: 12px 18px;
        background: #172033;
        color: #fff;
        font-weight: 700;
        cursor: pointer;
      }
      .quote-button:disabled {
        opacity: 0.55;
        cursor: not-allowed;
      }
      .options {
        display: grid;
        gap: 10px;
      }
      .option {
        display: flex;
        gap: 10px;
        align-items: flex-start;
        cursor: pointer;
      }
      .option-copy {
        flex: 1;
      }
      .option-heading {
        display: flex;
        justify-content: space-between;
        gap: 12px;
      }
      .free-badge {
        width: fit-content;
        padding: 3px 8px;
        border-radius: 999px;
        background: #e8f7ee;
        color: #18794e;
        font-size: 0.78rem;
        font-weight: 700;
      }
      .summary {
        display: grid;
        grid-template-columns: 1fr auto;
        gap: 8px 18px;
        margin: 0;
        padding-top: 16px;
        border-top: 1px solid #e6e9ee;
      }
      dd {
        margin: 0;
        text-align: right;
      }
      .total {
        padding-top: 8px;
        border-top: 1px solid #e6e9ee;
        font-size: 1.05rem;
        font-weight: 800;
      }
      .message {
        margin: 0;
        border-radius: 10px;
        padding: 10px 12px;
      }
      .error,
      small {
        color: #a21c24;
      }
      .error {
        background: #fff1f2;
      }
      @media (max-width: 620px) {
        .delivery-panel {
          padding: 16px;
        }
        .mode-grid {
          grid-template-columns: 1fr;
        }
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
    this.error.set('');
    this.selection.emit(null);
    this.quoted.emit(null);
  }

  quote() {
    if (this.mode() === 'SHIPPING') {
      this.address.markAllAsTouched();
      if (this.address.invalid) {
        this.error.set('Completá la dirección de entrega.');
        return;
      }
    }

    this.invalidate();
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

          const pickupAvailable = options.some((option) => option.type === 'PICKUP');
          const shippingAvailable = options.some((option) => option.type !== 'PICKUP');

          if (this.mode() === 'PICKUP' && !pickupAvailable) {
            this.mode.set('SHIPPING');
            if (this.address.invalid && !shippingAvailable) {
              this.error.set(
                'Este comercio trabaja con envío. Completá tu dirección para consultar las opciones disponibles.',
              );
              return;
            }
          } else if (this.mode() === 'SHIPPING' && !shippingAvailable && pickupAvailable) {
            this.mode.set('PICKUP');
          }

          if (!this.visibleOptions().length) {
            this.error.set('No hay métodos disponibles para este destino.');
          }
        },
        error: () => {
          if (generation !== this.generation) return;
          this.loading.set(false);
          this.error.set('No pudimos calcular el envío. Reintentá.');
        },
      });
  }

  select(q: ShippingQuote) {
    if (q.type !== 'PICKUP' && this.address.invalid) {
      this.address.markAllAsTouched();
      this.error.set('Completá la dirección de entrega antes de elegir el envío.');
      return;
    }
    this.error.set('');
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
