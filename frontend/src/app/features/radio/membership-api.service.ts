import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { switchMap } from 'rxjs';
import { CsrfService } from '../../core/auth/csrf.service';
export interface Plan { publicId: string; name: string; description: string; imageUrl: string | null; price: number; currency: string; benefits: string[]; active: boolean; displayOrder: number; }
export interface Period { publicId: string; periodYear: number; periodMonth: number; coverageStart: string; coverageEndExclusive: string; planPublicId: string; planNameSnapshot: string; amount: number; currency: string; accreditationStatus: 'PENDING' | 'ACCREDITED'; }
export type MemberState = 'NONE' | 'PENDING' | 'ACTIVE' | 'EXPIRED' | 'CANCELLED';
export interface Member { publicId: string | null; state: MemberState; plan: Plan | null; currentPeriod: Period | null; startedAt: string | null; cancelledAt: string | null; }
export interface AdminMember { membership: Member; identity: { firstName: string; lastName: string; email: string } | null; }
export type PlanInput = Omit<Plan, 'publicId'>;
export const stateLabel: Record<MemberState | 'ACCREDITED', string> = { NONE: 'Sin plan', PENDING: 'PENDIENTE', ACTIVE: 'ACTIVO', EXPIRED: 'VENCIDO', CANCELLED: 'CANCELADO', ACCREDITED: 'ACREDITADO' };
export function periodLabel(period: Period) { return new Intl.DateTimeFormat('es-AR', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(Date.UTC(period.periodYear, period.periodMonth - 1, 1))); }
export function coverageEnd(period: Period) { const date = new Date(period.coverageEndExclusive + 'T00:00:00Z'); date.setUTCDate(date.getUTCDate() - 1); return new Intl.DateTimeFormat('es-AR', { timeZone: 'UTC' }).format(date); }
@Injectable({ providedIn: 'root' })
export class MembershipApiService {
 private readonly http = inject(HttpClient);
 private readonly csrf = inject(CsrfService);
 private base(slug: string) { return '/api/v1/stores/' + encodeURIComponent(slug); }
 plans(slug: string, admin = false) { return this.http.get<Plan[]>(this.base(slug) + (admin ? '/admin' : '') + '/membership-plans'); }
 mine(slug: string) { return this.http.get<Member>(this.base(slug) + '/me/membership'); }
 periods(slug: string, offset = 0) { return this.http.get<Period[]>(this.base(slug) + '/me/membership/periods', { params: { offset } }); }
 choose(slug: string, planPublicId: string, change: boolean) { return this.write<Member>(change ? 'PUT' : 'POST', this.base(slug) + '/me/membership' + (change ? '/plan' : ''), { planPublicId }); }
 reactivate(slug: string, planPublicId: string) { return this.write<Member>('POST', this.base(slug) + '/me/membership/reactivate', { planPublicId }); }
 ensure(slug: string) { return this.write<Member>('POST', this.base(slug) + '/me/membership/current-period', {}); }
 cancel(slug: string) { return this.write<Member>('POST', this.base(slug) + '/me/membership/cancel', {}); }
 savePlan(slug: string, value: PlanInput, id?: string) { return this.write<Plan>(id ? 'PUT' : 'POST', this.base(slug) + '/admin/membership-plans' + (id ? '/' + encodeURIComponent(id) : ''), value); }
 members(slug: string, state = '', plan = '', offset = 0) {
  let params = new HttpParams().set('offset', offset);
  if (state) params = params.set('state', state);
  if (plan) params = params.set('planPublicId', plan);
  return this.http.get<AdminMember[]>(this.base(slug) + '/admin/paid-memberships', { params });
 }
 history(slug: string, id: string, offset = 0) { return this.http.get<Period[]>(this.base(slug) + '/admin/paid-memberships/' + encodeURIComponent(id) + '/periods', { params: { offset } }); }
 private write<T>(method: string, url: string, body: unknown) { return this.csrf.ensureToken().pipe(switchMap(() => this.http.request<T>(method, url, { body }))); }
}
