import { MembershipPaymentPanel } from './membership-payment-panel';
import { MembershipPaymentHistory } from './membership-payment-history';
import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { RadioContext } from './radio-context';
import { RadioAccountApiService } from './radio-account-api.service';
import { MembershipApiService, Member, Period, stateLabel, periodLabel, coverageEnd } from './membership-api.service';
@Component({ selector: 'app-membership-account', imports: [CurrencyPipe, RouterLink, MembershipPaymentPanel, MembershipPaymentHistory], styleUrl: './member-dashboard.scss', templateUrl: './membership-account-page.html' })
export class MembershipAccountPage {
 readonly context = inject(RadioContext); private readonly api = inject(MembershipApiService); private readonly profile = inject(RadioAccountApiService);
 readonly mode = inject(ActivatedRoute).snapshot.data['membershipMode'] ?? 'home';
 readonly name = signal(''); readonly member = signal<Member | null>(null); readonly periods = signal<Period[]>([]); readonly loading = signal(true); readonly busy = signal(false); readonly error = signal(''); readonly confirmCancel = signal(false); readonly offset = signal(0);
 readonly labels = stateLabel; readonly period = periodLabel; readonly end = coverageEnd;
 constructor() { this.profile.profile(this.context.slug()!).subscribe({ next: p => this.name.set(p.firstName), error: () => {} }); this.api.mine(this.context.slug()!).pipe(finalize(() => this.loading.set(false))).subscribe({ next: m => this.member.set(m), error: () => this.error.set('No pudimos cargar tu membresía.') }); if (this.mode === 'history') this.page(0); }
 page(delta: number) { this.offset.update(n => Math.max(0, n + delta)); this.busy.set(true); this.api.periods(this.context.slug()!, this.offset()).pipe(finalize(() => this.busy.set(false))).subscribe({ next: rows => this.periods.set(rows), error: () => this.error.set('No pudimos cargar las cuotas.') }); }
 reload() { this.api.mine(this.context.slug()!).subscribe({next:m=>this.member.set(m),error:()=>this.error.set('No pudimos actualizar tu membresía.')}); }
 ensure() { this.mutate(false); }
 cancel() { this.mutate(true); }
 private mutate(cancel: boolean) { if (this.busy()) return; this.busy.set(true); this.error.set(''); (cancel ? this.api.cancel(this.context.slug()!) : this.api.ensure(this.context.slug()!)).pipe(finalize(() => this.busy.set(false))).subscribe({ next: m => { this.member.set(m); this.confirmCancel.set(false); }, error: e => this.error.set(e.error?.detail || 'No pudimos completar la operación.') }); }
}
