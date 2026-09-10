import { provideHttpClient, HttpErrorResponse } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, ActivatedRouteSnapshot, RouterStateSnapshot, Router, UrlTree, provideRouter, convertToParamMap } from '@angular/router';
import { firstValueFrom, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../core/auth/auth.service';
import { CsrfService } from '../../core/auth/csrf.service';
import { membershipGuard, superAdminGuard } from '../../core/auth/auth.guards';
import { StorefrontRoutingService } from '../storefront/storefront-routing.service';
import { RadioAccountApiService } from './radio-account-api.service';
import { RadioAuthPage } from './radio-auth-page';
import { RadioPrivatePage } from './radio-private-page';
import { RadioLayout } from './radio-layout';
import { RadioContext } from './radio-context';
import { radioAccountGuard } from './radio-account.guard';

const profile = { firstName: 'Ana', lastName: 'Perez', email: 'ana@example.com', phone: null };
const identity = { authenticated: true, user: { id: 'u', email: profile.email, displayName: 'Ana', platformRole: 'USER' }, memberships: [] };
const context = { slug: () => 'radio-a', link: (...parts: string[]) => ['/tiendas', 'radio-a', ...parts] };

describe('RADIO identity transport', () => {
  let api: RadioAccountApiService;
  let http: HttpTestingController;
  const csrf = { ensureToken: vi.fn(() => of(undefined)) };
  beforeEach(() => {
    csrf.ensureToken.mockClear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), { provide: CsrfService, useValue: csrf }] });
    api = TestBed.inject(RadioAccountApiService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('reads only /me at the encoded tenant path', () => {
    api.profile('radio/a').subscribe(value => expect(value).toEqual(profile));
    http.expectOne('/api/v1/stores/radio%2Fa/me/profile').flush(profile);
    expect(csrf.ensureToken).not.toHaveBeenCalled();
  });
  it('protects all four mutations with CSRF and sends the expected DTOs', () => {
    const { email, ...update } = profile;
    const registration = { ...profile, password: 'correct-password' };
    api.register('radio-a', registration).subscribe();
    api.update('radio-a', update).subscribe();
    api.forgot('radio-a', email).subscribe();
    api.reset('radio-a', 'token', 'new-password').subscribe();
    const expected = [
      ['member-registration', 'POST', registration], ['me/profile', 'PUT', update],
      ['account/password/forgot', 'POST', { email }], ['account/password/reset', 'POST', { token: 'token', password: 'new-password' }],
    ] as const;
    for (const [path, method, body] of expected) {
      const request = http.expectOne('/api/v1/stores/radio-a/' + path);
      expect(request.request.method).toBe(method); expect(request.request.body).toEqual(body); request.flush({});
    }
    expect(csrf.ensureToken).toHaveBeenCalledTimes(4);
  });
});

describe('RADIO forms and navigation', () => {
  let api: { register: ReturnType<typeof vi.fn>; forgot: ReturnType<typeof vi.fn>; reset: ReturnType<typeof vi.fn>; profile: ReturnType<typeof vi.fn>; update: ReturnType<typeof vi.fn> };
  let auth: { login: ReturnType<typeof vi.fn>; logout: ReturnType<typeof vi.fn>; loadSession: ReturnType<typeof vi.fn>; markAnonymous: ReturnType<typeof vi.fn>; isAuthenticated: () => boolean };
  let navigate: ReturnType<typeof vi.spyOn>;
  let route: { snapshot: { data: Record<string, unknown>; fragment: string | null } };
  beforeEach(() => {
    api = { register: vi.fn(() => of({})), forgot: vi.fn(() => of({})), reset: vi.fn(() => of(undefined)), profile: vi.fn(() => of(profile)), update: vi.fn(() => of(profile)) };
    auth = { login: vi.fn(() => of(identity)), logout: vi.fn(() => of(undefined)), loadSession: vi.fn(() => of(identity)), markAnonymous: vi.fn(), isAuthenticated: () => true };
    route = { snapshot: { data: { mode: 'register' }, fragment: null } };
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ActivatedRoute, useValue: route }, { provide: RadioContext, useValue: context }, { provide: RadioAccountApiService, useValue: api }, { provide: AuthService, useValue: auth }] });
    TestBed.overrideComponent(RadioLayout, { set: { providers: [{ provide: RadioContext, useValue: context }] } });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });
  function page(mode: string, token?: string) {
    route.snapshot.data = { mode }; route.snapshot.fragment = token ? 'token=' + token : null;
    return TestBed.createComponent(RadioAuthPage);
  }
  it('requires registration fields and matching passwords, without granting roles', () => {
    const fixture = page('register'); fixture.detectChanges();
    const component = fixture.componentInstance;
    component.submit(); expect(api.register).not.toHaveBeenCalled();
    component.form.patchValue({ ...profile, phone: '', password: 'correct-password', confirmation: 'different' });
    component.submit(); expect(component.error()).toContain('no coinciden');
    component.form.controls.confirmation.setValue('correct-password'); component.submit();
    expect(api.register).toHaveBeenCalledWith('radio-a', { ...profile, password: 'correct-password' });
    expect(component.message()).toContain('Solicitud recibida'); expect(component.form.controls.password.value).toBe('');
  });
  it('rejects malformed email and short password', () => {
    const component = page('register').componentInstance;
    component.form.patchValue({ firstName: 'A', lastName: 'B', email: 'invalid', password: 'short', confirmation: 'short' });
    component.submit(); expect(api.register).not.toHaveBeenCalled();
  });
  it('uses global login and returns to the current RADIO account', () => {
    const component = page('login').componentInstance;
    component.form.patchValue({ email: profile.email, password: 'existing' }); component.submit();
    expect(auth.login).toHaveBeenCalledWith({ email: profile.email, password: 'existing' });
    expect(navigate).toHaveBeenCalledWith(['/tiendas', 'radio-a', 'mi-cuenta']);
  });
  it('shows a safe login error', () => {
    auth.login.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    const component = page('login').componentInstance;
    component.form.patchValue({ email: profile.email, password: 'wrong' }); component.submit();
    expect(component.error()).toContain('no son válidos'); expect(component.busy()).toBe(false);
  });
  it('shows a generic forgot-password confirmation', () => {
    const component = page('forgot').componentInstance;
    component.form.controls.email.setValue(profile.email); component.submit();
    expect(api.forgot).toHaveBeenCalledWith('radio-a', profile.email); expect(component.message()).toContain('Si existe');
  });
  it('removes the fragment secret, resets once and clears local authentication', () => {
    const token = 'a'.repeat(43); const component = page('reset', token).componentInstance;
    expect(navigate).toHaveBeenCalledWith([], expect.objectContaining({ replaceUrl: true, fragment: undefined }));
    component.form.patchValue({ password: 'new-correct-password', confirmation: 'new-correct-password' }); component.submit();
    expect(api.reset).toHaveBeenCalledWith('radio-a', token, 'new-correct-password'); expect(auth.markAnonymous).toHaveBeenCalled();
    component.submit(); expect(api.reset).toHaveBeenCalledTimes(1);
  });
  it('rejects a reset without a valid link', () => {
    const component = page('reset').componentInstance;
    component.form.patchValue({ password: 'new-correct-password', confirmation: 'new-correct-password' }); component.submit();
    expect(api.reset).not.toHaveBeenCalled(); expect(component.error()).toContain('no es válido');
  });
  it('renders readonly email and saves only editable global fields', () => {
    route.snapshot.data = { profile: true };
    const fixture = TestBed.createComponent(RadioPrivatePage); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('input[type=email]').readOnly).toBe(true);
    fixture.componentInstance.form.patchValue({ firstName: ' Ana Maria ', phone: ' 123 ' }); fixture.componentInstance.save();
    expect(api.update).toHaveBeenCalledWith('radio-a', { firstName: 'Ana Maria', lastName: 'Perez', phone: '123' });
    expect(fixture.componentInstance.saved()).toBe(true);
  });
  it('shows only the own profile greeting in the private placeholder', () => {
    const fixture = TestBed.createComponent(RadioPrivatePage); fixture.detectChanges();
    expect(api.profile).toHaveBeenCalledWith('radio-a'); expect(fixture.nativeElement.textContent).toContain('Hola, Ana');
    expect(fixture.nativeElement.textContent).toContain('Tu cuenta está activa');
  });
  it('keeps registration and navigation usable at the browser viewport', () => {
    // Geometry is verified by the browser runs; jsdom has no layout engine.
    if (navigator.userAgent.includes('jsdom')) return;
    const layout = TestBed.createComponent(RadioLayout); layout.detectChanges();
    const registration = page('register'); registration.detectChanges();
    const main = layout.nativeElement.querySelector('main');
    main.appendChild(registration.nativeElement);
    document.body.appendChild(layout.nativeElement);
    const fields = registration.nativeElement.querySelector('.fields') as HTMLElement;
    const columns = getComputedStyle(fields).gridTemplateColumns.split(' ');
    expect(columns.length).toBe(window.innerWidth <= 600 ? 1 : 2);
    for (const element of layout.nativeElement.querySelectorAll('input, button, nav a')) {
      const rect = element.getBoundingClientRect();
      expect(rect.width).toBeGreaterThan(0);
      expect(rect.left).toBeGreaterThanOrEqual(0);
      expect(rect.right).toBeLessThanOrEqual(window.innerWidth + 1);
    }
    expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(window.innerWidth);
    expect(getComputedStyle(layout.nativeElement.querySelector('nav')).flexWrap).toBe('wrap');
  });
  it('closes the existing session and redirects to RADIO login', () => {
    const fixture = TestBed.createComponent(RadioLayout); fixture.detectChanges(); fixture.componentInstance.logout();
    expect(auth.logout).toHaveBeenCalledOnce(); expect(navigate).toHaveBeenCalledWith(['/tiendas', 'radio-a']);
  });
});

describe('RADIO authorization boundary', () => {
  const snapshot = { paramMap: convertToParamMap({ storeSlug: 'radio-a' }), parent: null } as ActivatedRouteSnapshot;
  const state = { url: '/tiendas/radio-a/mi-cuenta' } as RouterStateSnapshot;
  function setup(authenticated: boolean) {
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AuthService, useValue: { loadSession: () => of(authenticated ? identity : { authenticated: false }) } }, { provide: StorefrontRoutingService, useValue: { route: (slug: string, path: string) => ['/tiendas', slug, path] } }] });
  }
  it('redirects visitors to tenant login', async () => {
    setup(false);
    const result = await firstValueFrom(TestBed.runInInjectionContext(() => radioAccountGuard(snapshot, state)) as Observable<UrlTree>);
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/radio-a/login');
  });
  it('allows an ordinary identity into /mi-cuenta, but denies both admin areas', async () => {
    setup(true);
    expect(await firstValueFrom(TestBed.runInInjectionContext(() => radioAccountGuard(snapshot, state)) as Observable<boolean>)).toBe(true);
    for (const guard of [membershipGuard, superAdminGuard]) {
      const result = await firstValueFrom(TestBed.runInInjectionContext(() => guard(snapshot, state)) as Observable<UrlTree>);
      expect(TestBed.inject(Router).serializeUrl(result)).toBe('/admin/comercios?denied=true');
    }
  });
});
