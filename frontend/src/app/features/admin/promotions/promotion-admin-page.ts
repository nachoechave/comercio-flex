import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';

type PromotionScope = 'PRODUCT' | 'PRODUCTS' | 'CATEGORY' | 'COMBO';

interface Promotion {
  id: string;
  scopeType: PromotionScope;
  productId: string | null;
  productName: string | null;
  categoryId: string | null;
  categoryName: string | null;
  productIds: string[];
  secondProductIds?: string[];
  name: string;
  bundleQuantity: number;
  bundlePrice: number;
  active: boolean;
  startsAt: string | null;
  endsAt: string | null;
  version: number;
}

interface ProductPage {
  items: Array<{ id: string; name: string; status: string }>;
}

interface Category {
  id: string;
  name: string;
  slug: string;
  active: boolean;
}

@Component({
  selector: 'app-promotion-admin-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <section class="page">
      <header>
        <div>
          <p class="eyebrow">VENTAS</p>
          <h1>Promociones</h1>
          <p>Creá promos para un producto, varios productos o una categoría completa.</p>
        </div>
        <button type="button" class="primary" (click)="newPromotion()">Nueva promoción</button>
      </header>

      @if (message()) { <p class="notice success">{{ message() }}</p> }
      @if (error()) { <p class="notice error">{{ error() }}</p> }

      <div class="layout">
        <section class="card">
          <h2>{{ editingId() ? 'Editar promoción' : 'Nueva promoción' }}</h2>

          <form [formGroup]="form" (ngSubmit)="save()">
            <label>
              Alcance
              <select formControlName="scopeType">
                <option value="PRODUCT">Un producto</option>
                <option value="PRODUCTS">Varios productos</option>
                <option value="CATEGORY">Categoría completa</option>
                <option value="COMBO">Combo de dos grupos (1 + 1)</option>
              </select>
            </label>

            @if (form.controls.scopeType.value === 'PRODUCT') {
              <label>
                Producto
                <select formControlName="productId">
                  <option value="">Elegí un producto</option>
                  @for (product of products(); track product.id) {
                    <option [value]="product.id">{{ product.name }}</option>
                  }
                </select>
              </label>
            }

            @if (form.controls.scopeType.value === 'PRODUCTS' || form.controls.scopeType.value === 'COMBO') {
              <label>
                {{ form.controls.scopeType.value === 'COMBO' ? 'Grupo 1 (ej: remeras)' : 'Productos incluidos' }}
                <select multiple size="7" formControlName="productIds">
                  @for (product of products(); track product.id) {
                    <option [value]="product.id">{{ product.name }}</option>
                  }
                </select>
                <small>Mantené Ctrl/Cmd para seleccionar varios. Elegí al menos dos productos.</small>
              </label>
            }

            @if (form.controls.scopeType.value === 'CATEGORY') {
              <label>
                Categoría
                <select formControlName="categoryId">
                  <option value="">Elegí una categoría</option>
                  @for (category of categories(); track category.id) {
                    <option [value]="category.id">{{ category.name }}</option>
                  }
                </select>
                <small>
                  La promo alcanza automáticamente a los productos actuales y futuros de esta categoría.
                </small>
              </label>
            }

            @if (form.controls.scopeType.value === 'COMBO') {
              <label>Grupo 2 (ej: pantalones)
                <select multiple size="7" formControlName="secondProductIds">
                  @for (product of products(); track product.id) {
                    <option [value]="product.id">{{ product.name }}</option>
                  }
                </select>
                <small>El combo exige una unidad de cada grupo. No repitas productos entre grupos.</small>
              </label>
            }
            <label>
              Nombre
              <input
                formControlName="name"
                maxlength="120"
                placeholder="Ej: 2 pantalones por $60.000"
              />
            </label>

            <div class="two">
              <label>
                Cantidad
                <input type="number" min="2" max="99" step="1" formControlName="bundleQuantity" [readOnly]="form.controls.scopeType.value === 'COMBO'" />
              </label>

              <label>
                Precio promocional
                <input type="number" min="0.01" step="0.01" formControlName="bundlePrice" />
              </label>
            </div>

            <div class="preview">
              @if (form.controls.bundleQuantity.value && form.controls.bundlePrice.value) {
                <strong>
                  Llevando {{ form.controls.bundleQuantity.value }}:
                  $ {{ form.controls.bundlePrice.value | number:'1.2-2' }}
                </strong>
                <span>{{ previewHelp() }}</span>
              }
            </div>

            <div class="two">
              <label>
                Desde
                <input type="datetime-local" formControlName="startsAt" />
              </label>
              <label>
                Hasta
                <input type="datetime-local" formControlName="endsAt" />
              </label>
            </div>

            <label class="check">
              <input type="checkbox" formControlName="active" />
              Promoción activa
            </label>

            <div class="actions">
              <button class="primary" type="submit" [disabled]="busy() || form.invalid">
                {{ busy() ? 'Guardando…' : (editingId() ? 'Guardar cambios' : 'Crear promoción') }}
              </button>
              @if (editingId()) {
                <button type="button" class="secondary" (click)="newPromotion()">Cancelar</button>
              }
            </div>
          </form>
        </section>

        <section class="card list">
          <h2>Promociones configuradas</h2>

          @if (loading()) {
            <p>Cargando…</p>
          } @else if (!promotions().length) {
            <p class="empty">Todavía no hay promociones.</p>
          } @else {
            @for (promo of promotions(); track promo.id) {
              <article>
                <div>
                  <strong>{{ promo.name }}</strong>
                  <span>{{ targetLabel(promo) }}</span>
                  <small>
                    {{ promo.bundleQuantity }} por $ {{ promo.bundlePrice | number:'1.2-2' }}
                    · {{ promo.active ? 'Activa' : 'Pausada' }}
                  </small>
                  @if (promo.startsAt || promo.endsAt) {
                    <small>
                      @if (promo.startsAt) { Desde {{ promo.startsAt | date:'short' }} }
                      @if (promo.endsAt) { · Hasta {{ promo.endsAt | date:'short' }} }
                    </small>
                  }
                </div>

                <div class="row-actions">
                  <button type="button" class="secondary" (click)="edit(promo)">Editar</button>
                  <button type="button" class="secondary" (click)="toggle(promo)">
                    {{ promo.active ? 'Pausar' : 'Activar' }}
                  </button>
                </div>
              </article>
            }
          }
        </section>
      </div>
    </section>
  `,
  styles: [`
    :host { display:block; }
    .page { display:grid; gap:1.25rem; }
    header { display:flex; justify-content:space-between; align-items:flex-start; gap:1rem; }
    h1,h2,p { margin-top:0; }
    h1 { margin-bottom:.4rem; font-size:clamp(1.8rem,3vw,2.6rem); }
    .eyebrow { margin-bottom:.35rem; font-size:.75rem; font-weight:800; letter-spacing:.12em; opacity:.65; }
    .layout { display:grid; grid-template-columns:minmax(18rem,.85fr) minmax(22rem,1.15fr); gap:1rem; align-items:start; }
    .card { padding:1.25rem; border:1px solid #dfe3ea; border-radius:1rem; background:#fff; box-shadow:0 .5rem 1.5rem rgb(15 23 42 / 5%); }
    form { display:grid; gap:1rem; }
    label { display:grid; gap:.4rem; font-weight:700; }
    label small { color:#64748b; font-weight:500; line-height:1.4; }
    input,select { width:100%; min-height:2.75rem; padding:.65rem .75rem; border:1px solid #cbd5e1; border-radius:.7rem; background:#fff; font:inherit; }
    select[multiple] { min-height:11rem; }
    .two { display:grid; grid-template-columns:1fr 1fr; gap:.8rem; }
    .check { display:flex; align-items:center; gap:.55rem; }
    .check input { width:1rem; min-height:1rem; }
    .preview { display:grid; gap:.25rem; padding:.8rem; border-radius:.75rem; background:#f8fafc; }
    .preview span { font-size:.83rem; color:#64748b; }
    button { min-height:2.5rem; padding:.55rem .9rem; border-radius:.7rem; font:inherit; font-weight:800; cursor:pointer; }
    .primary { border:1px solid #111827; color:#fff; background:#111827; }
    .secondary { border:1px solid #cbd5e1; color:#111827; background:#fff; }
    .actions,.row-actions { display:flex; gap:.55rem; flex-wrap:wrap; }
    .list { display:grid; gap:.8rem; }
    article { display:flex; justify-content:space-between; gap:1rem; padding:1rem 0; border-top:1px solid #e5e7eb; }
    article:first-of-type { border-top:0; }
    article > div:first-child { display:grid; gap:.2rem; }
    article span, article small { color:#64748b; }
    .notice { margin:0; padding:.75rem 1rem; border-radius:.7rem; }
    .success { color:#166534; background:#f0fdf4; }
    .error { color:#991b1b; background:#fef2f2; }
    .empty { color:#64748b; }
    @media (max-width: 54rem) {
      .layout { grid-template-columns:1fr; }
      header { flex-direction:column; }
    }
    @media (max-width: 36rem) {
      .two { grid-template-columns:1fr; }
      article { flex-direction:column; }
    }
  `],
})
export class PromotionAdminPage {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  readonly promotions = signal<Promotion[]>([]);
  readonly products = signal<Array<{ id: string; name: string; status: string }>>([]);
  readonly categories = signal<Category[]>([]);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    scopeType: ['PRODUCT' as PromotionScope, Validators.required],
    productId: [''],
    productIds: [[] as string[]],
    secondProductIds: [[] as string[]],
    categoryId: [''],
    name: ['', [Validators.required, Validators.maxLength(120)]],
    bundleQuantity: [2, [Validators.required, Validators.min(2), Validators.max(99)]],
    bundlePrice: [0, [Validators.required, Validators.min(0.01)]],
    active: [true],
    startsAt: [''],
    endsAt: [''],
  });

  private get storeSlug(): string {
    return this.route.parent?.snapshot.paramMap.get('storeSlug') ?? '';
  }

  constructor() {
    this.form.controls.scopeType.valueChanges.subscribe(scope => {
      if (scope === 'COMBO') this.form.controls.bundleQuantity.setValue(2);
    });
    this.load();
  }

  newPromotion(): void {
    this.editingId.set(null);
    this.resetForm();
    this.message.set(null);
    this.error.set(null);
  }

  edit(promo: Promotion): void {
    this.editingId.set(promo.id);
    this.form.reset({
      scopeType: promo.scopeType,
      productId: promo.productId ?? '',
      productIds: ['PRODUCTS', 'COMBO'].includes(promo.scopeType) ? promo.productIds : [],
      secondProductIds: promo.secondProductIds ?? [],
      categoryId: promo.categoryId ?? '',
      name: promo.name,
      bundleQuantity: promo.bundleQuantity,
      bundlePrice: Number(promo.bundlePrice),
      active: promo.active,
      startsAt: this.toLocalInput(promo.startsAt),
      endsAt: this.toLocalInput(promo.endsAt),
    });
    this.message.set(null);
    this.error.set(null);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  save(): void {
    if (this.form.invalid || this.busy()) return;

    const value = this.form.getRawValue();
    if (value.scopeType === 'PRODUCT' && !value.productId) {
      this.error.set('Elegí un producto.');
      return;
    }
    if (value.scopeType === 'PRODUCTS' && value.productIds.length < 2) {
      this.error.set('Elegí al menos dos productos.');
      return;
    }
    if (value.scopeType === 'CATEGORY' && !value.categoryId) {
      this.error.set('Elegí una categoría.');
      return;
    }

    if (value.scopeType === 'COMBO' && (!value.productIds.length || !value.secondProductIds.length || value.secondProductIds.some(id => value.productIds.includes(id)))) {
      this.error.set('Elegí productos en ambos grupos sin repetirlos.');
      return;
    }
    const current = this.promotions().find((item) => item.id === this.editingId());
    const body = {
      scopeType: value.scopeType,
      productId: value.scopeType === 'PRODUCT' ? value.productId : null,
      productIds: ['PRODUCTS', 'COMBO'].includes(value.scopeType) ? value.productIds : [],
      secondProductIds: value.scopeType === 'COMBO' ? value.secondProductIds : [],
      categoryId: value.scopeType === 'CATEGORY' ? value.categoryId : null,
      name: value.name.trim(),
      bundleQuantity: value.scopeType === 'COMBO' ? 2 : Number(value.bundleQuantity),
      bundlePrice: Number(value.bundlePrice),
      active: value.active,
      startsAt: value.startsAt ? new Date(value.startsAt).toISOString() : null,
      endsAt: value.endsAt ? new Date(value.endsAt).toISOString() : null,
      ...(current ? { version: current.version } : {}),
    };

    this.busy.set(true);
    this.error.set(null);

    const request = current
      ? this.http.put<Promotion>(`${this.baseUrl()}/${encodeURIComponent(current.id)}`, body)
      : this.http.post<Promotion>(this.baseUrl(), body);

    request.subscribe({
      next: () => {
        this.busy.set(false);
        this.editingId.set(null);
        this.resetForm();
        this.message.set(current ? 'Promoción actualizada.' : 'Promoción creada.');
        this.load(false);
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(
          err?.error?.detail ||
          err?.error?.message ||
          'No pudimos guardar la promoción.',
        );
      },
    });
  }

  toggle(promo: Promotion): void {
    if (this.busy()) return;

    this.busy.set(true);
    this.error.set(null);

    this.http.patch<Promotion>(
      `${this.baseUrl()}/${encodeURIComponent(promo.id)}/status`,
      { active: !promo.active, version: promo.version },
    ).subscribe({
      next: () => {
        this.busy.set(false);
        this.load(false);
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err?.error?.detail || 'No pudimos cambiar el estado.');
      },
    });
  }

  previewHelp(): string {
    switch (this.form.controls.scopeType.value) {
      case 'COMBO':
        return 'Una unidad de cada grupo. Dos productos del mismo grupo no forman el combo.';
      case 'CATEGORY':
        return 'Se combinan unidades de cualquier producto de la categoría elegida.';
      case 'PRODUCTS':
        return 'Se combinan unidades de cualquiera de los productos seleccionados.';
      default:
        return 'Las variantes del mismo producto se combinan para alcanzar la cantidad.';
    }
  }

  targetLabel(promo: Promotion): string {
    if (promo.scopeType === 'COMBO') return 'Combo: 1 producto de cada grupo';
    if (promo.scopeType === 'CATEGORY') {
      return `Categoría: ${promo.categoryName ?? 'Sin categoría'}`;
    }
    if (promo.scopeType === 'PRODUCTS') {
      return `${promo.productIds.length} productos incluidos`;
    }
    return promo.productName ?? 'Producto';
  }

  private resetForm(): void {
    this.form.reset({
      scopeType: 'PRODUCT',
      productId: '',
      productIds: [],
      secondProductIds: [],
      categoryId: '',
      name: '',
      bundleQuantity: 2,
      bundlePrice: 0,
      active: true,
      startsAt: '',
      endsAt: '',
    });
  }

  private load(showLoading = true): void {
    if (!this.storeSlug) return;
    if (showLoading) this.loading.set(true);

    forkJoin({
      promotions: this.http.get<Promotion[]>(this.baseUrl()),
      products: this.http.get<ProductPage>(
        `/api/v1/stores/${encodeURIComponent(this.storeSlug)}/admin/products?page=0&size=100&status=ALL`,
      ),
      categories: this.http.get<Category[]>(
        `/api/v1/stores/${encodeURIComponent(this.storeSlug)}/admin/categories`,
      ),
    }).subscribe({
      next: ({ promotions, products, categories }) => {
        this.promotions.set(promotions);
        this.products.set(products.items);
        this.categories.set(categories);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.detail || 'No pudimos cargar las promociones.');
      },
    });
  }

  private baseUrl(): string {
    return `/api/v1/stores/${encodeURIComponent(this.storeSlug)}/admin/promotions`;
  }

  private toLocalInput(value: string | null): string {
    if (!value) return '';
    const date = new Date(value);
    const offset = date.getTimezoneOffset() * 60_000;
    return new Date(date.getTime() - offset).toISOString().slice(0, 16);
  }
}
