import { Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { routeParam } from '../../../../core/auth/auth.guards';
import { inheritedRouteParam } from '../../../../core/routing/inherited-route-param';
import { InventoryApiService } from '../inventory-api.service';
import { BranchPayload, StoreBranch } from '../inventory.models';

@Component({
  selector: 'app-branch-management',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="page" aria-labelledby="branches-title">
      <nav><a [routerLink]="['/tiendas', storeSlug(), 'admin', 'inventario']">Inventario</a> / Sucursales</nav>
      <header><div><p class="eyebrow">Inventario físico</p><h1 id="branches-title">Sucursales</h1><p>El stock se controla por local. La sucursal Principal abastece las ventas online en esta fase.</p></div></header>

      @if (message()) { <p class="notice">{{ message() }}</p> }
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }

      <div class="grid">
        <section class="card">
          <h2>{{ editing() ? 'Editar sucursal' : 'Nueva sucursal' }}</h2>
          <form [formGroup]="form" (ngSubmit)="save()">
            <label>Nombre<input formControlName="name" maxlength="120" /></label>
            <label>Dirección<input formControlName="address" maxlength="255" /></label>
            @if (editing()) { <label class="check"><input type="checkbox" formControlName="active" /> Sucursal activa</label> }
            <label class="check"><input type="checkbox" formControlName="defaultBranch" /> Usar como Principal para ventas online</label>
            <div class="actions"><button type="submit" [disabled]="saving()">{{ saving() ? 'Guardando…' : 'Guardar' }}</button>@if (editing()) {<button type="button" class="secondary" (click)="cancelEdit()">Cancelar</button>}</div>
          </form>
        </section>

        <section class="card">
          <h2>Locales configurados</h2>
          @if (loading()) { <p>Cargando…</p> }
          @for (branch of branches(); track branch.id) {
            <article class="branch">
              <div><strong>{{ branch.name }}</strong>@if (branch.defaultBranch) { <span>Principal</span> }<p>{{ branch.address || 'Sin dirección cargada' }} · {{ branch.active ? 'Activa' : 'Inactiva' }}</p></div>
              <button type="button" class="secondary" (click)="edit(branch)">Editar</button>
            </article>
          }
        </section>
      </div>
    </section>
  `,
  styles: `
    .page{display:grid;gap:1.25rem}.eyebrow{font-weight:700;color:var(--color-accent)}h1,h2,p{margin:.25rem 0}.grid{display:grid;grid-template-columns:minmax(18rem,28rem) 1fr;gap:1rem}.card{border:1px solid var(--color-border);border-radius:1rem;padding:1.25rem;background:var(--color-surface);display:grid;gap:1rem}form,label{display:grid;gap:.4rem}form{gap:1rem}.check{display:flex;align-items:center;gap:.5rem}.branch{display:flex;justify-content:space-between;gap:1rem;padding:1rem 0;border-bottom:1px solid var(--color-border)}.branch span{margin-left:.5rem;padding:.2rem .5rem;border-radius:999px;background:var(--color-accent-soft, #eef4ff);font-size:.8rem}.branch p{color:var(--color-muted);font-size:.9rem}.actions{display:flex;gap:.5rem}.secondary{background:transparent;color:inherit;border:1px solid var(--color-border)}.notice,.error{padding:.75rem 1rem;border-radius:.75rem;border:1px solid var(--color-border)}.error{color:var(--color-danger, #a21d1d)}@media(max-width:800px){.grid{grid-template-columns:1fr}.branch{align-items:flex-start}}
  `,
})
export class BranchManagement {
  private readonly api = inject(InventoryApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), { initialValue: routeParam(this.route.snapshot, 'storeSlug') ?? '' });
  readonly branches = signal<StoreBranch[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly editing = signal<StoreBranch | null>(null);
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly form = this.fb.nonNullable.group({ name: ['', [Validators.required, Validators.maxLength(120)]], address: ['', [Validators.maxLength(255)]], active: [true], defaultBranch: [false] });

  constructor() { effect(() => { const slug = this.storeSlug(); if (slug) this.load(slug); }); }

  edit(branch: StoreBranch): void {
    this.editing.set(branch); this.message.set(null); this.error.set(null);
    this.form.setValue({ name: branch.name, address: branch.address ?? '', active: branch.active, defaultBranch: branch.defaultBranch });
  }
  cancelEdit(): void { this.editing.set(null); this.form.reset({ name: '', address: '', active: true, defaultBranch: false }); }
  save(): void {
    this.form.markAllAsTouched(); if (this.form.invalid || this.saving()) return;
    const slug = this.storeSlug(); if (!slug) return;
    const value = this.form.getRawValue();
    const body: BranchPayload = { name: value.name.trim(), address: value.address.trim() || null, active: value.active, defaultBranch: value.defaultBranch };
    const current = this.editing(); this.saving.set(true); this.error.set(null); this.message.set(null);
    const request = current ? this.api.updateBranch(slug, current.id, body) : this.api.createBranch(slug, { ...body, active: true });
    request.pipe(finalize(() => this.saving.set(false))).subscribe({ next: () => { this.message.set(current ? 'Sucursal actualizada.' : 'Sucursal creada.'); this.cancelEdit(); this.load(slug); }, error: () => this.error.set('No pudimos guardar la sucursal.') });
  }
  private load(slug: string): void { this.loading.set(true); this.api.branches(slug).pipe(finalize(() => this.loading.set(false))).subscribe({ next: (items) => this.branches.set(items), error: () => this.error.set('No pudimos cargar las sucursales.') }); }
}
