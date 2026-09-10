import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { RadioContext } from './radio-context';
import { RadioAccountApiService } from './radio-account-api.service';
import { MembershipApiService, Member, Period, stateLabel, periodLabel, coverageEnd } from './membership-api.service';
@Component({ selector: 'app-membership-account', imports: [CurrencyPipe, RouterLink], styleUrl: './membership.scss', template: `
 <section class="card"><h1>{{ mode === 'history' ? 'Cuotas' : mode === 'plan' ? 'Mi plan' : 'Hola, ' + name() }}</h1>
 @if (loading()) { <p role="status">Cargando membresía…</p> }
 @if (error()) { <p role="alert">{{ error() }}</p> }
 @if (member(); as m) {
 @if (m.state === 'NONE') { <p>No tenés un plan seleccionado.</p><a [routerLink]="context.link('socios')">Ver planes</a> }
 @else {
 <p class="status">{{ labels[m.state] }}</p>
 @if (mode !== 'history') {
 <h2>{{ m.currentPeriod?.planNameSnapshot || m.plan?.name }}</h2>
 @if (m.currentPeriod; as p) { <p>{{ period(p) }}</p><p class="amount">{{ p.amount | currency:p.currency }}</p><p>{{ labels[p.accreditationStatus] }}</p><p>{{ p.accreditationStatus === 'ACCREDITED' ? 'Válido hasta' : 'Cuota correspondiente hasta' }} {{ end(p) }}</p> }
 @else { <p>No hay cuota para el mes actual.</p> }
 @if (mode === 'plan' && m.plan; as plan) { <p>{{ plan.description }}</p><ul>@for (b of plan.benefits; track $index) { <li>{{ b }}</li> }</ul><p>Plan seleccionado para próximas cuotas: {{ plan.name }} · {{ plan.price | currency:plan.currency }}</p> }
 @if (m.state !== 'CANCELLED') {
 <div class="actions"><a [routerLink]="context.link('socios')">Cambiar plan</a>
 @if (!m.currentPeriod) { <button (click)="ensure()" [disabled]="busy()">Generar cuota del mes</button> }
 <button class="secondary" (click)="confirmCancel.set(true)" [disabled]="busy()">Cancelar membresía</button></div>
 @if (confirmCancel()) { <p>La cancelación impide crear nuevas cuotas y conserva tu historial.</p><button (click)="cancel()" [disabled]="busy()">Confirmar cancelación</button><button class="secondary" (click)="confirmCancel.set(false)">Volver</button> }
 } @else { <p>La membresía está cancelada. Tu historial se conserva.</p> }
 }
 @if (mode === 'history') {
 <div class="table-wrap"><table><thead><tr><th>Período</th><th>Plan</th><th>Importe</th><th>Estado</th><th>Cobertura hasta</th></tr></thead><tbody>
 @for (p of periods(); track p.publicId) { <tr><td>{{ period(p) }}</td><td>{{ p.planNameSnapshot }}</td><td>{{ p.amount | currency:p.currency }}</td><td>{{ labels[p.accreditationStatus] }}</td><td>{{ end(p) }}</td></tr> }
 </tbody></table></div><div class="actions"><button (click)="page(-100)" [disabled]="offset() === 0 || busy()">Anterior</button><button (click)="page(100)" [disabled]="periods().length < 100 || busy()">Siguiente</button></div>
 }
 <p>Pago online disponible próximamente.</p>
 }
 }
 </section>` })
export class MembershipAccountPage {
 readonly context = inject(RadioContext); private readonly api = inject(MembershipApiService); private readonly profile = inject(RadioAccountApiService);
 readonly mode = inject(ActivatedRoute).snapshot.data['membershipMode'] ?? 'home';
 readonly name = signal(''); readonly member = signal<Member | null>(null); readonly periods = signal<Period[]>([]); readonly loading = signal(true); readonly busy = signal(false); readonly error = signal(''); readonly confirmCancel = signal(false); readonly offset = signal(0);
 readonly labels = stateLabel; readonly period = periodLabel; readonly end = coverageEnd;
 constructor() { this.profile.profile(this.context.slug()!).subscribe({ next: p => this.name.set(p.firstName), error: () => {} }); this.api.mine(this.context.slug()!).pipe(finalize(() => this.loading.set(false))).subscribe({ next: m => this.member.set(m), error: () => this.error.set('No pudimos cargar tu membresía.') }); if (this.mode === 'history') this.page(0); }
 page(delta: number) { this.offset.update(n => Math.max(0, n + delta)); this.busy.set(true); this.api.periods(this.context.slug()!, this.offset()).pipe(finalize(() => this.busy.set(false))).subscribe({ next: rows => this.periods.set(rows), error: () => this.error.set('No pudimos cargar las cuotas.') }); }
 ensure() { this.mutate(false); }
 cancel() { this.mutate(true); }
 private mutate(cancel: boolean) { if (this.busy()) return; this.busy.set(true); this.error.set(''); (cancel ? this.api.cancel(this.context.slug()!) : this.api.ensure(this.context.slug()!)).pipe(finalize(() => this.busy.set(false))).subscribe({ next: m => { this.member.set(m); this.confirmCancel.set(false); }, error: e => this.error.set(e.error?.detail || 'No pudimos completar la operación.') }); }
}
