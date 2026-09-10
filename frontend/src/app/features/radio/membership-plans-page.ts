import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { RadioContext } from './radio-context';
import { MembershipApiService, Plan, Member } from './membership-api.service';
@Component({ selector: 'app-membership-plans', imports: [CurrencyPipe], styleUrl: './membership.scss', template: `
 <section><h1>Socios</h1><p>Elegí el plan con el que querés acompañar a la radio.</p>
 @if (error()) { <p role="alert">{{ error() }}</p> }
 @if (loading()) { <p role="status">Cargando planes…</p> }
 <div class="plan-grid">
 @for (plan of plans(); track plan.publicId) {
 <article class="card"><h2>{{ plan.name }}</h2><p class="amount">{{ plan.price | currency:plan.currency }} / mes</p><p>{{ plan.description }}</p>
 <ul>@for (benefit of plan.benefits; track $index) { <li>{{ benefit }}</li> }</ul>
 <button (click)="choose(plan)" [disabled]="busy()">Elegir plan</button></article>
 } @empty { @if (!loading()) { <p>No hay planes disponibles por el momento.</p> } }
 </div>
 @if (selected(); as plan) {
 <section class="card confirmation" aria-label="Confirmar plan"><h2>Elegiste {{ plan.name }}</h2><p>{{ plan.price | currency:plan.currency }} por mes</p>
 <p>Se generará una cuota mensual pendiente. El pago online estará disponible próximamente.</p>
 @if (member()?.currentPeriod?.accreditationStatus === 'ACCREDITED') { <p>Tu cuota acreditada conserva su plan e importe. El cambio aplica a la próxima cuota.</p> }
 <button (click)="confirm()" [disabled]="busy()">{{ busy() ? 'Guardando…' : 'Confirmar' }}</button>
 <button class="secondary" (click)="selected.set(null)" [disabled]="busy()">Volver</button></section>
 }</section>` })
export class MembershipPlansPage {
 readonly context = inject(RadioContext);
 private readonly api = inject(MembershipApiService);
 private readonly auth = inject(AuthService);
 private readonly router = inject(Router);
 readonly plans = signal<Plan[]>([]); readonly selected = signal<Plan | null>(null); readonly member = signal<Member | null>(null);
 readonly loading = signal(true); readonly busy = signal(false); readonly error = signal('');
 constructor() { this.api.plans(this.context.slug()!).pipe(finalize(() => this.loading.set(false))).subscribe({ next: plans => this.plans.set(plans.filter(p => p.active)), error: () => this.error.set('No pudimos cargar los planes.') }); }
 choose(plan: Plan) {
  this.busy.set(true); this.error.set('');
  this.auth.loadSession(true).subscribe({ next: session => {
   if (!session.authenticated) { this.busy.set(false); void this.router.navigate(this.context.link('ingresar'), { queryParams: { next: 'socios' } }); return; }
   this.api.mine(this.context.slug()!).pipe(finalize(() => this.busy.set(false))).subscribe({ next: member => { this.member.set(member); this.selected.set(plan); }, error: () => this.error.set('No pudimos consultar tu membresía.') });
  }, error: () => { this.busy.set(false); this.error.set('No pudimos comprobar tu sesión.'); } });
 }
 confirm() {
  if (!this.selected() || !this.member() || this.busy()) return;
  this.busy.set(true); this.error.set('');
  this.api.choose(this.context.slug()!, this.selected()!.publicId, this.member()!.state !== 'NONE').pipe(finalize(() => this.busy.set(false))).subscribe({ next: () => void this.router.navigate(this.context.link('mi-cuenta')), error: e => this.error.set(e.error?.detail || 'No pudimos confirmar el plan. Volvé a cargar los planes e intentá nuevamente.') });
 }
}
