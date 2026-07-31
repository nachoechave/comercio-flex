import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { vi } from 'vitest';

import { PaymentAuthorizationNavigationService } from '../payment-authorization-navigation.service';
import { PaymentConnection } from '../payment-connection.models';
import { PaymentConnectionPage } from './payment-connection-page';

const CONNECTED: PaymentConnection = {
  provider: 'MERCADO_PAGO',
  environment: 'TEST',
  status: 'CONNECTED',
  connectedAccountLabel: 'TESTABC123',
  connectedAt: '2026-07-31T12:00:00Z',
};

const NOT_CONNECTED: PaymentConnection = {
  provider: 'MERCADO_PAGO',
  environment: 'TEST',
  status: 'NOT_CONNECTED',
  connectedAccountLabel: null,
  connectedAt: null,
};

describe('PaymentConnectionPage', () => {
  let fixture: ComponentFixture<PaymentConnectionPage>;
  let http: HttpTestingController;
  let storeParams: BehaviorSubject<ParamMap>;
  let queryParams: ParamMap;
  let navigateExternal: ReturnType<typeof vi.fn>;
  let routerNavigate: ReturnType<typeof vi.fn>;

  function createComponent(): void {
    fixture = TestBed.createComponent(PaymentConnectionPage);
    fixture.detectChanges();
  }

  function flushConnection(connection: PaymentConnection): void {
    http.expectOne('/api/v1/stores/tienda-a/admin/payment-connection').flush(connection);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    storeParams = new BehaviorSubject(convertToParamMap({ storeSlug: 'tienda-a' }));
    queryParams = convertToParamMap({});
    navigateExternal = vi.fn();
    routerNavigate = vi.fn().mockResolvedValue(true);
    const parent = {
      paramMap: convertToParamMap({ storeSlug: 'tienda-a' }),
      parent: null,
    };
    await TestBed.configureTestingModule({
      imports: [PaymentConnectionPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            pathFromRoot: [{ paramMap: storeParams.asObservable() }],
            snapshot: {
              paramMap: convertToParamMap({}),
              queryParamMap: { get: (name: string) => queryParams.get(name) },
              parent,
            },
          },
        },
        { provide: Router, useValue: { navigate: routerNavigate } },
        {
          provide: PaymentAuthorizationNavigationService,
          useValue: { navigate: navigateExternal },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('renders the verified connected identity and environment', () => {
    createComponent();
    expect(fixture.nativeElement.querySelector('[role="status"]')).toBeTruthy();
    flushConnection(CONNECTED);

    const text = fixture.nativeElement.textContent.replace(/\s+/g, ' ');
    expect(text).toContain('Conectada a: TESTABC123');
    expect(fixture.nativeElement.querySelector('dt').textContent).toContain('Ambiente:');
    expect(fixture.nativeElement.querySelector('dd').textContent).toContain('Prueba');
    expect(text).not.toContain('access_token');
  });

  it('uses a safe fallback when the verified label is unavailable', () => {
    createComponent();
    flushConnection({ ...CONNECTED, connectedAccountLabel: '   ' });

    const text = fixture.nativeElement.textContent.replace(/\s+/g, ' ');
    expect(text).toContain('Conectada a: Cuenta de Mercado Pago verificada');
    expect(text).not.toContain('undefined');
    expect(text).not.toContain('null');
  });

  it('starts authorization only after obtaining CSRF and uses same-tab navigation', () => {
    createComponent();
    flushConnection(NOT_CONNECTED);

    const connect = fixture.nativeElement.querySelector('.primary') as HTMLButtonElement;
    connect.click();
    connect.click();
    const csrf = http.expectOne('/api/v1/auth/csrf');
    expect(csrf.request.method).toBe('GET');
    csrf.flush(null);
    const authorization = http.expectOne(
      '/api/v1/stores/tienda-a/admin/payment-connection/authorization',
    );
    expect(authorization.request.method).toBe('POST');
    authorization.flush({
      authorizationUrl: 'https://auth.mercadopago.com.ar/authorization?state=opaque',
      expiresAt: '2026-07-31T12:10:00Z',
    });
    fixture.detectChanges();

    expect(navigateExternal).toHaveBeenCalledOnce();
    expect(navigateExternal).toHaveBeenCalledWith(
      'https://auth.mercadopago.com.ar/authorization?state=opaque',
    );
  });

  it('requires inline confirmation and reloads authoritative state after disconnecting', () => {
    createComponent();
    flushConnection(CONNECTED);

    (fixture.nativeElement.querySelector('.danger') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alertdialog"]')).toBeTruthy();

    (fixture.nativeElement.querySelector('.confirmation .danger') as HTMLButtonElement).click();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    const deletion = http.expectOne('/api/v1/stores/tienda-a/admin/payment-connection');
    expect(deletion.request.method).toBe('DELETE');
    deletion.flush(null);
    http.expectOne('/api/v1/stores/tienda-a/admin/payment-connection').flush(NOT_CONNECTED);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('fue desconectada');
    expect(fixture.nativeElement.querySelector('[role="alertdialog"]')).toBeNull();
  });

  it('does not announce OAuth success before the backend confirms it', () => {
    queryParams = convertToParamMap({ oauth: 'connected' });
    createComponent();
    flushConnection(NOT_CONNECTED);

    expect(fixture.nativeElement.textContent).toContain('no pudimos confirmar la conexión');
    expect(fixture.nativeElement.textContent).not.toContain('quedó conectada correctamente');
    expect(routerNavigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ replaceUrl: true, queryParams: { oauth: null } }),
    );
  });

  it('keeps the verified identity visible when reauthorization is required', () => {
    createComponent();
    flushConnection({ ...CONNECTED, status: 'REAUTHORIZATION_REQUIRED' });

    const text = fixture.nativeElement.textContent.replace(/\s+/g, ' ');
    expect(text).toContain('Conectada a: TESTABC123');
    expect(text).toContain('Volver a autorizar');
  });
});
