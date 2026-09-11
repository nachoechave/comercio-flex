import { MembershipPaymentHistory } from './membership-payment-history';
import { MembershipPaymentApi, MembershipPaymentSettings } from './membership-payment-api.service';
import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { routeParam } from '../../core/auth/auth.guards';
import { MembershipApiService, Plan, AdminMember, Period, PlanInput, stateLabel, periodLabel, coverageEnd } from './membership-api.service';
@Component({ selector: 'app-membership-admin', imports: [CurrencyPipe, ReactiveFormsModule, MembershipPaymentHistory, RouterLink], styleUrl: './membership.scss', template: `
 <section><h1>{{ mode === 'plans' ? 'Planes' : mode === 'periods' ? 'Cuotas de socios' : 'Socios' }}</h1>
 @if (error()) { <p role="alert">{{ error() }}</p> }
 @if (loading()) { <p role="status">Cargando…</p> }
 @if (mode === 'plans') {
 <button (click)="edit()">Crear plan</button>
 <div class="table-wrap"><table><thead><tr><th>Nombre</th><th>Precio</th><th>Moneda</th><th>Estado</th><th>Orden</th><th>Acciones</th></tr></thead><tbody>
 @for (p of plans(); track p.publicId) { <tr><td>{{ p.name }}</td><td>{{ p.price | currency:p.currency }}</td><td>{{ p.currency }}</td><td>{{ p.active ? 'Activo' : 'Inactivo' }}</td><td>{{ p.displayOrder }}</td><td><div class="actions"><button (click)="edit(p)">Editar</button><button (click)="toggle(p)" [disabled]="busy()">{{ p.active ? 'Desactivar' : 'Activar' }}</button></div></td></tr> }
 </tbody></table></div>
 @if (editing()) {
 <section class="card"><h2>{{ editingId() ? 'Editar plan' : 'Nuevo plan' }}</h2><form [formGroup]="form" (ngSubmit)="save()">
 <label>Nombre<input formControlName="name" maxlength="120" required /></label>
 <label>Descripción<textarea formControlName="description" maxlength="2000"></textarea></label>
 <label>Imagen del plan (URL)<input formControlName="imageUrl" type="url" maxlength="1000" placeholder="https://..." /><small>Se muestra en la home y en la selección de planes.</small></label>
 <label>Precio mensual<input formControlName="price" type="number" min="0" step="0.01" required /></label>
 <label>Moneda<input formControlName="currency" readonly /></label>
 <label>Beneficios (uno por línea)<textarea formControlName="benefits" rows="5"></textarea></label>
 <label>Orden<input formControlName="displayOrder" type="number" min="0" max="1000000" required /></label>
 <label>Activo<input formControlName="active" type="checkbox" /></label>
 @if (form.touched && form.invalid) { <p role="alert">Revisá los campos del plan.</p> }
 <div class="actions"><button type="submit" [disabled]="busy()">Guardar</button><button type="button" class="secondary" (click)="editing.set(false)">Volver</button></div>
 </form></section>
 }
 } @else {
 @if (paymentSettings(); as settings) { <section class="card payment-settings" aria-label="Pagos de membresías"><h2>Pago online</h2><p>{{ settings.available ? 'Disponible para socios' : settings.credentialAvailable ? 'Deshabilitado para esta radio' : 'Mercado Pago no está conectado' }}</p>@if (settings.credentialAvailable) { <button (click)="togglePayments()" [disabled]="busy()">{{ settings.enabled ? 'Deshabilitar pagos' : 'Habilitar pagos' }}</button> } @else { <p>Conectá primero la cuenta de Mercado Pago de esta radio para habilitar el cobro online.</p><a class="button primary" [routerLink]="['/tiendas', slug, 'admin', 'configuracion', 'pagos']">Conectar Mercado Pago</a> }</section> }
 <div class="filters"><label>Estado<select [value]="state()" (change)="filterState($any($event.target).value)"><option value="">Todos</option>@for (s of states; track s) { <option [value]="s">{{ labels[s] }}</option> }</select></label>
 <label>Plan<select [value]="planFilter()" (change)="filterPlan($any($event.target).value)"><option value="">Todos</option>@for (p of plans(); track p.publicId) { <option [value]="p.publicId">{{ p.name }}</option> }</select></label></div>
 <div class="table-wrap"><table><thead><tr><th>Socio</th><th>Email</th><th>Plan</th><th>Estado</th><th>Período</th><th>Monto</th><th>Vigencia</th><th>Acciones</th></tr></thead><tbody>
 @for (row of members(); track row.membership.publicId) {
 <tr><td>{{ row.identity ? row.identity.firstName + ' ' + row.identity.lastName : 'Identidad no disponible' }}</td><td>{{ row.identity?.email }}</td><td>{{ row.membership.currentPeriod?.planNameSnapshot || row.membership.plan?.name }}</td><td>{{ labels[row.membership.state] }} @if (row.membership.cancelledAt) { <small>{{ row.membership.cancelledAt }}</small> }</td>
 <td>{{ row.membership.currentPeriod ? period(row.membership.currentPeriod!) : 'Sin cuota actual' }}</td><td>@if (row.membership.currentPeriod; as p) { {{ p.amount | currency:p.currency }} }</td><td>{{ row.membership.currentPeriod ? end(row.membership.currentPeriod!) : '—' }}</td><td><button (click)="history(row)">Ver cuotas</button></td></tr>
 } @empty { @if (!loading()) { <tr><td colspan="8">No hay socios para estos filtros.</td></tr> } }
 </tbody></table></div><div class="actions"><button (click)="page(-100)" [disabled]="offset() === 0 || loading()">Anterior</button><button (click)="page(100)" [disabled]="members().length < 100 || loading()">Siguiente</button></div>
 @if (selected(); as row) {
 <section class="card"><h2>Cuotas de {{ row.identity?.firstName }}</h2><app-membership-payment-history [slug]="slug" [membershipId]="row.membership.publicId!"/><div class="table-wrap"><table><thead><tr><th>Período</th><th>Plan</th><th>Importe</th><th>Estado</th></tr></thead><tbody>
 @for (p of periods(); track p.publicId) { <tr><td>{{ period(p) }}</td><td>{{ p.planNameSnapshot }}</td><td>{{ p.amount | currency:p.currency }}</td><td>{{ labels[p.accreditationStatus] }}</td></tr> }
 </tbody></table></div><div class="actions"><button (click)="history(row, -100)" [disabled]="historyOffset() === 0 || busy()">Anteriores</button><button (click)="history(row, 100)" [disabled]="periods().length < 100 || busy()">Más cuotas</button></div></section>
 }
 }</section>` })
export class MembershipAdminPage {
 private readonly api = inject(MembershipApiService); private readonly paymentApi = inject(MembershipPaymentApi); private readonly route = inject(ActivatedRoute);
 readonly slug = routeParam(this.route.snapshot, 'storeSlug')!;
 readonly mode = this.route.snapshot.data['membershipAdminMode'] ?? 'members';
 private readonly currency = this.route.parent?.snapshot.data['tenantSettings']?.currencyCode ?? '';
 readonly plans = signal<Plan[]>([]); readonly members = signal<AdminMember[]>([]); readonly periods = signal<Period[]>([]); readonly selected = signal<AdminMember | null>(null);
 readonly paymentSettings = signal<MembershipPaymentSettings | null>(null);
 readonly error = signal(''); readonly loading = signal(false); readonly busy = signal(false); readonly editing = signal(false); readonly editingId = signal<string | undefined>(undefined);
 readonly state = signal(''); readonly planFilter = signal(''); readonly offset = signal(0); readonly historyOffset = signal(0);
 readonly labels = stateLabel; readonly period = periodLabel; readonly end = coverageEnd; readonly states = ['ACTIVE', 'PENDING', 'EXPIRED', 'CANCELLED'] as const;
 readonly form = new FormGroup({ name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(120)] }), description: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(2000)] }), imageUrl: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(1000)] }), price: new FormControl(0, { nonNullable: true, validators: [Validators.required, Validators.min(0), Validators.max(9999999999.99), Validators.pattern(/^\d+(\.\d{1,2})?$/)] }), currency: new FormControl(this.currency, { nonNullable: true }), benefits: new FormControl('', { nonNullable: true }), active: new FormControl(true, { nonNullable: true }), displayOrder: new FormControl(0, { nonNullable: true, validators: [Validators.required, Validators.min(0), Validators.max(1000000), Validators.pattern(/^\d+$/)] }) });
 constructor() { this.reloadPlans(); if (this.mode !== 'plans') { this.loadMembers(); this.loadPaymentSettings(); } }
 loadPaymentSettings() { this.paymentApi.settings(this.slug).subscribe({ next: settings => this.paymentSettings.set(settings), error: () => this.paymentSettings.set(null) }); }
 togglePayments() { const current = this.paymentSettings(); if (!current || this.busy()) return; this.busy.set(true); this.paymentApi.enable(this.slug, !current.enabled).pipe(finalize(() => this.busy.set(false))).subscribe({ next: settings => this.paymentSettings.set(settings), error: () => this.error.set('No pudimos actualizar la disponibilidad de pagos.') }); }
 reloadPlans() { this.loading.set(true); this.api.plans(this.slug, true).pipe(finalize(() => this.loading.set(false))).subscribe({ next: plans => this.plans.set(plans), error: () => this.error.set('No pudimos cargar los planes.') }); }
 edit(plan?: Plan) { this.editingId.set(plan?.publicId); this.form.reset({ name: plan?.name ?? '', description: plan?.description ?? '', imageUrl: plan?.imageUrl ?? '', price: plan?.price ?? 0, currency: plan?.currency ?? this.currency, benefits: plan?.benefits.join('\n') ?? '', active: plan?.active ?? true, displayOrder: plan?.displayOrder ?? 0 }); this.editing.set(true); }
 save() { this.form.markAllAsTouched(); if (this.form.invalid || this.busy()) return; const v = this.form.getRawValue(); const benefits = v.benefits.split('\n').map(s => s.trim()).filter(Boolean); if (benefits.length > 30 || benefits.some(s => s.length > 240)) { this.error.set('Usá hasta 30 beneficios de 240 caracteres.'); return; } this.persist({ ...v, benefits }, this.editingId()); }
 toggle(plan: Plan) { const { publicId, ...value } = plan; this.persist({ name: value.name, description: value.description, imageUrl: value.imageUrl, price: value.price, currency: value.currency, benefits: value.benefits, displayOrder: value.displayOrder, active: !value.active }, publicId); }
 private persist(value: PlanInput, id?: string) { this.busy.set(true); this.error.set(''); this.api.savePlan(this.slug, value, id).pipe(finalize(() => this.busy.set(false))).subscribe({ next: () => { this.editing.set(false); this.reloadPlans(); }, error: e => this.error.set(e.error?.detail || 'No pudimos guardar el plan.') }); }
 filterState(state: string) { this.state.set(state); this.offset.set(0); this.loadMembers(); }
 filterPlan(plan: string) { this.planFilter.set(plan); this.offset.set(0); this.loadMembers(); }
 page(delta: number) { this.offset.update(n => Math.max(0, n + delta)); this.loadMembers(); }
 loadMembers() { this.loading.set(true); this.selected.set(null); this.api.members(this.slug, this.state(), this.planFilter(), this.offset()).pipe(finalize(() => this.loading.set(false))).subscribe({ next: rows => this.members.set(rows), error: () => this.error.set('No pudimos cargar los socios.') }); }
 history(row: AdminMember, delta = 0) { if (delta === 0) this.historyOffset.set(0); else this.historyOffset.update(n => Math.max(0, n + delta)); this.selected.set(row); this.busy.set(true); this.api.history(this.slug, row.membership.publicId!, this.historyOffset()).pipe(finalize(() => this.busy.set(false))).subscribe({ next: periods => this.periods.set(periods), error: () => this.error.set('No pudimos cargar las cuotas.') }); }
}
