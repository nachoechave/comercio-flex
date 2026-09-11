import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { MembershipApiService, Plan, Member, Period, AdminMember, coverageEnd, periodLabel } from './membership-api.service';
import { MembershipPlansPage } from './membership-plans-page';
import { MembershipAccountPage } from './membership-account-page';
import { MembershipAdminPage } from './membership-admin-page';
import { RadioContext } from './radio-context';
import { RadioAccountApiService } from './radio-account-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { CsrfService } from '../../core/auth/csrf.service';
import { MembershipPaymentApi } from './membership-payment-api.service';
import { AdminLayout } from '../../layouts/admin-layout/admin-layout';
import { RadioHomePage } from './radio-home-page';
import { RadioSiteApiService } from './radio-site-api.service';
import { StorefrontContextService } from '../storefront/storefront-context.service';
const plan: Plan = { publicId: 'plan-1', name: 'PLUS', price: 6000, currency: 'ARS', description: 'Acompañá a la radio', benefits: ['Comunidad', 'Eventos'], active: true, displayOrder: 1 };
const period: Period = { publicId: 'period-1', periodYear: 2026, periodMonth: 9, coverageStart: '2026-09-01', coverageEndExclusive: '2026-10-01', planPublicId: plan.publicId, planNameSnapshot: 'PLUS', amount: 6000, currency: 'ARS', accreditationStatus: 'PENDING' };
const member: Member = { publicId: 'member-1', state: 'PENDING', plan, currentPeriod: period, startedAt: '2026-09-15T12:00:00Z', cancelledAt: null };
const row: AdminMember = { membership: member, identity: { firstName: 'Ana', lastName: 'Perez', email: 'ana@example.com' } };
const context = { slug: () => 'radio-a', link: (...parts: string[]) => ['/tiendas', 'radio-a', ...parts] };
describe('RADIO membership screens', () => {
 let api: Record<string, ReturnType<typeof vi.fn>>; let auth: Record<string, ReturnType<typeof vi.fn>>;
 let route: any; let router: Router;
 beforeEach(() => {
  api = { plans: vi.fn(() => of([plan])), mine: vi.fn(() => of(member)), choose: vi.fn(() => of(member)), periods: vi.fn(() => of([period])), ensure: vi.fn(() => of(member)), cancel: vi.fn(() => of({ ...member, state: 'CANCELLED' })), savePlan: vi.fn(() => of(plan)), members: vi.fn(() => of([row])), history: vi.fn(() => of([period])) };
  auth = { loadSession: vi.fn(() => of({ authenticated: true })), membershipFor: vi.fn(() => ({ storeSlug: 'radio-a', storeName: 'Radio A', role: 'OWNER' })), user: vi.fn(() => ({ displayName: 'Admin' })), memberships: vi.fn(() => []), logout: vi.fn(() => of(undefined)) };
  const data = { tenantSettings: { tenantType: 'RADIO', currencyCode: 'ARS' } };
  route = { snapshot: { data: {}, paramMap: convertToParamMap({ storeSlug: 'radio-a' }), parent: null }, parent: { snapshot: { data } }, data: of(data), paramMap: of(convertToParamMap({ storeSlug: 'radio-a' })) };
  TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: MembershipApiService, useValue: api }, { provide: MembershipPaymentApi, useValue: { state: vi.fn(() => of({ available: false, status: 'NOT_STARTED', checkoutUrl: null, message: '' })), checkout: vi.fn(() => of({ available: false, status: 'NOT_STARTED', checkoutUrl: null, message: '' })), settings: vi.fn(() => of({ enabled: false, credentialAvailable: false, available: false })), enable: vi.fn(() => of({ enabled: true, credentialAvailable: true, available: true })), history: vi.fn(() => of([])), adminHistory: vi.fn(() => of({ payments: [], attempts: [] })) } }, { provide: RadioAccountApiService, useValue: { profile: () => of({ firstName: 'Ana' }) } }, { provide: AuthService, useValue: auth }, { provide: RadioContext, useValue: context }, { provide: ActivatedRoute, useValue: route }] });
  TestBed.configureTestingModule({ providers: [
   { provide: RadioSiteApiService, useValue: { get: () => of({ settings: { heroTitle: 'Nuestra radio' }, programs: [], sponsors: [], team: [] }) } },
   { provide: StorefrontContextService, useValue: { settings: () => ({ branding: { heroImageUrl: '/configured-hero.jpg' } }) } },
  ] });
  router = TestBed.inject(Router); vi.spyOn(router, 'navigate').mockResolvedValue(true);
 });
 it('renders backend plans and filters inactive entries', () => {
  api['plans'].mockReturnValue(of([plan, { ...plan, publicId: 'hidden', name: 'Oculto', active: false }]));
  const f = TestBed.createComponent(MembershipPlansPage); f.detectChanges();
  expect(f.nativeElement.textContent).toContain('PLUS'); expect(f.nativeElement.textContent).toContain('Comunidad'); expect(f.nativeElement.textContent).not.toContain('Oculto');
 });
 it.each(['NONE', 'PENDING', 'ACTIVE'] as const)('keeps home CTAs scoped to the real %s membership and renders plan benefits', state => {
  api['mine'].mockReturnValue(of({ ...member, state }));
  const f = TestBed.createComponent(RadioHomePage); f.detectChanges();
  const cta = f.nativeElement.querySelector('.mini-plan a');
  expect(cta.getAttribute('href')).toBe('/tiendas/radio-a/' + (state === 'NONE' ? 'socios' : 'mi-cuenta'));
  expect(cta.textContent).toContain(state === 'NONE' ? 'Ver plan' : 'Mi membresía');
  expect(f.nativeElement.querySelector('.mini-plan').textContent).toContain('Comunidad');
  expect(f.nativeElement.querySelector('.radio-hero').style.backgroundImage).toContain('/configured-hero.jpg');
  expect(f.nativeElement.querySelector('.editorial-section')).toBeNull();
  expect(f.nativeElement.querySelector('.sponsors')).toBeNull();
  expect(api['choose']).not.toHaveBeenCalled();
 });
 it('shows real dashboard benefits and an active navigation item without inventing paid data', () => {
  const f = TestBed.createComponent(MembershipAccountPage); f.detectChanges();
  expect(f.nativeElement.querySelector('.member-sidebar [aria-current="page"]').textContent).toContain('Inicio');
  expect(f.nativeElement.querySelectorAll('.benefit-grid article')).toHaveLength(2);
  expect(f.nativeElement.querySelector('.membership-pass .status').dataset.state).toBe('PENDING');
  expect(f.nativeElement.querySelector('.membership-pass').textContent).not.toContain('Próximo cobro');
  expect(f.nativeElement.textContent).not.toContain('Sorteos activos');
 });
 it('redirects a visitor to RADIO login with a bounded return destination', () => {
  auth['loadSession'].mockReturnValue(of({ authenticated: false })); const f = TestBed.createComponent(MembershipPlansPage); f.componentInstance.choose(plan);
  expect(router.navigate).toHaveBeenCalledWith(['/tiendas', 'radio-a', 'ingresar'], { queryParams: { next: 'socios' } }); expect(api['choose']).not.toHaveBeenCalled();
 });
 it('requires confirmation and chooses only a public plan ID', () => {
  api['mine'].mockReturnValue(of({ ...member, state: 'NONE', publicId: null })); const f = TestBed.createComponent(MembershipPlansPage); f.componentInstance.choose(plan); f.detectChanges();
  expect(f.nativeElement.textContent).toContain('Elegiste PLUS'); expect(api['choose']).not.toHaveBeenCalled();
  f.componentInstance.confirm(); expect(api['choose']).toHaveBeenCalledWith('radio-a', 'plan-1', false); expect(router.navigate).toHaveBeenCalledWith(['/tiendas', 'radio-a', 'mi-cuenta']);
 });
 it('uses explicit change for an existing membership', () => {
  const f = TestBed.createComponent(MembershipPlansPage); f.componentInstance.choose(plan); f.componentInstance.confirm(); expect(api['choose']).toHaveBeenCalledWith('radio-a', 'plan-1', true);
 });
 it('shows the empty account without creating a commercial relation', () => {
  api['mine'].mockReturnValue(of({ ...member, state: 'NONE', plan: null, currentPeriod: null })); const f = TestBed.createComponent(MembershipAccountPage); f.detectChanges(); expect(f.nativeElement.textContent).toContain('No tenés un plan seleccionado'); expect(f.nativeElement.querySelector('.member-content a').getAttribute('href')).toBe('/tiendas/radio-a/socios'); expect(api['choose']).not.toHaveBeenCalled();
 });
 it.each(['PENDING', 'ACTIVE'] as const)('renders %s using the current period snapshot', state => {
  api['mine'].mockReturnValue(of({ ...member, state, plan: { ...plan, name: 'NUEVO', price: 8000 }, currentPeriod: { ...period, accreditationStatus: state === 'ACTIVE' ? 'ACCREDITED' : 'PENDING' } })); const f = TestBed.createComponent(MembershipAccountPage); f.detectChanges();
  expect(f.nativeElement.textContent).toContain('Hola, Ana'); expect(f.nativeElement.textContent).toContain('PLUS'); expect(f.nativeElement.textContent).toContain(state === 'ACTIVE' ? 'ACTIVO' : 'PENDIENTE'); expect(f.nativeElement.textContent).toContain('30/9/2026'); expect(f.nativeElement.textContent).not.toContain('Pagar con Mercado Pago');
 });
 it('shows selected plan details and preserves historical amount', () => {
  route.snapshot.data = { membershipMode: 'plan' }; const f = TestBed.createComponent(MembershipAccountPage); f.detectChanges(); expect(f.nativeElement.textContent).toContain('Mi plan'); expect(f.nativeElement.textContent).toContain('Comunidad'); expect(f.nativeElement.textContent).toContain('próximas cuotas');
 });
 it('shows monthly history without payment-provider fields', () => {
  route.snapshot.data = { membershipMode: 'history' }; const f = TestBed.createComponent(MembershipAccountPage); f.detectChanges(); expect(f.nativeElement.querySelector('table tbody tr')).toBeTruthy(); expect(f.nativeElement.textContent).toContain('septiembre de 2026'); expect(api['periods']).toHaveBeenCalledWith('radio-a', 0);
 });
 it('only creates the current month after an explicit action', () => {
  api['mine'].mockReturnValue(of({ ...member, state: 'EXPIRED', currentPeriod: null })); const f = TestBed.createComponent(MembershipAccountPage); f.detectChanges(); expect(api['ensure']).not.toHaveBeenCalled(); f.componentInstance.ensure(); expect(api['ensure']).toHaveBeenCalledWith('radio-a');
 });
 it('cancels logically and hides creation controls', () => {
  const f = TestBed.createComponent(MembershipAccountPage); f.componentInstance.cancel(); f.detectChanges(); expect(f.nativeElement.textContent).toContain('CANCELADO'); expect(f.nativeElement.textContent).not.toContain('Cambiar plan');
 });
 it('admin edits plans with validated fields and descriptive benefits', () => {
  route.snapshot.data = { membershipAdminMode: 'plans' }; const f = TestBed.createComponent(MembershipAdminPage); f.componentInstance.edit(plan); f.componentInstance.form.patchValue({ name: 'SOCIO ORO', price: 8000, benefits: 'Uno\nDos' }); f.componentInstance.save();
  expect(api['savePlan']).toHaveBeenCalledWith('radio-a', expect.objectContaining({ name: 'SOCIO ORO', price: 8000, benefits: ['Uno', 'Dos'] }), 'plan-1');
 });
 it('admin rejects negative prices and disables without deleting', () => {
  route.snapshot.data = { membershipAdminMode: 'plans' }; const f = TestBed.createComponent(MembershipAdminPage); f.componentInstance.edit(plan); f.componentInstance.form.controls.price.setValue(-1); f.componentInstance.save(); expect(api['savePlan']).not.toHaveBeenCalled(); f.componentInstance.toggle(plan); expect(api['savePlan']).toHaveBeenCalledWith('radio-a', expect.objectContaining({ active: false }), 'plan-1');
 });
 it('admin lists identity, filters by state and plan, and opens history', () => {
  const f = TestBed.createComponent(MembershipAdminPage); f.detectChanges(); expect(f.nativeElement.textContent).toContain('ana@example.com'); f.componentInstance.filterState('ACTIVE'); f.componentInstance.filterPlan('plan-1'); expect(api['members']).toHaveBeenLastCalledWith('radio-a', 'ACTIVE', 'plan-1', 0); f.componentInstance.history(row); expect(api['history']).toHaveBeenCalledWith('radio-a', 'member-1', 0);
 });
 it.each(['RADIO', 'ECOMMERCE'])('composes %s navigation centrally', type => {
  route.data = of({ tenantSettings: { tenantType: type } }); const f = TestBed.createComponent(AdminLayout); f.detectChanges(); const text = f.nativeElement.querySelector('nav').textContent;
  if (type === 'RADIO') { expect(text).toContain('Socios'); expect(text).toContain('Planes'); expect(text).toContain('Cuotas'); expect(text).not.toContain('Productos'); } else { expect(text).toContain('Productos'); expect(text).toContain('Pedidos'); expect(text).not.toContain('Socios'); }
 });
 it('keeps cards, account, mobile navigation and admin tables within the real viewport', () => {
  if (navigator.userAgent.includes('jsdom')) return;
  for (const component of [MembershipPlansPage, MembershipAccountPage]) {
   const f = TestBed.createComponent(component as typeof MembershipPlansPage); f.detectChanges(); expect(f.nativeElement.getBoundingClientRect().width).toBeLessThanOrEqual(window.innerWidth); for (const card of f.nativeElement.querySelectorAll('.card')) expect(card.getBoundingClientRect().right).toBeLessThanOrEqual(window.innerWidth + 1); f.destroy();
  }
  const layout = TestBed.createComponent(AdminLayout); layout.detectChanges(); const admin = TestBed.createComponent(MembershipAdminPage); admin.detectChanges(); layout.nativeElement.querySelector('main').appendChild(admin.nativeElement); document.body.appendChild(layout.nativeElement);
  expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(window.innerWidth); expect(getComputedStyle(admin.nativeElement.querySelector('.table-wrap')).overflowX).toBe('auto');
  layout.componentInstance.toggleMobileNavigation(); layout.detectChanges(); expect(layout.componentInstance.mobileNavigationOpen()).toBe(true); layout.componentInstance.closeNavigationWithEscape(); expect(layout.componentInstance.mobileNavigationOpen()).toBe(false);
 });
});
describe('RADIO membership API', () => {
 it('protects writes with CSRF, scopes URLs and sends only planPublicId', () => {
  const csrf = { ensureToken: vi.fn(() => of(undefined)) }; TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), { provide: CsrfService, useValue: csrf }] });
  const api = TestBed.inject(MembershipApiService); const http = TestBed.inject(HttpTestingController);
  api.choose('radio/a', 'plan-1', false).subscribe(); const request = http.expectOne('/api/v1/stores/radio%2Fa/me/membership'); expect(request.request.body).toEqual({ planPublicId: 'plan-1' }); expect(csrf.ensureToken).toHaveBeenCalledOnce(); request.flush(member); http.verify();
 });
 it('formats exclusive coverage as the last included calendar date', () => { expect(coverageEnd(period)).toBe('30/9/2026'); expect(periodLabel(period)).toBe('septiembre de 2026'); });
});
