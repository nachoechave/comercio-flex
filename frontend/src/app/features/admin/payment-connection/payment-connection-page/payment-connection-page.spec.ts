import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { vi } from 'vitest';

import { PaymentAuthorizationNavigationService } from '../payment-authorization-navigation.service';
import { PaymentConnection, PaymentWebhookEventSummary } from '../payment-connection.models';
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

const DEAD_WEBHOOK: PaymentWebhookEventSummary = {
  eventId: 'evt-safe-42',
  status: 'DEAD',
  attemptCount: 8,
  safeErrorCode: 'MP_TEMPORARY_FAILURE',
  occurredAt: '2026-08-01T12:00:00Z',
  retryAllowed: true,
};

describe('PaymentConnectionPage', () => {
  let fixture: ComponentFixture<PaymentConnectionPage>;
  let http: HttpTestingController;
  let storeParams: BehaviorSubject<ParamMap>;
  let queryParams: ParamMap;
  let navigateExternal: ReturnType<typeof vi.fn>;
  let routerNavigate: ReturnType<typeof vi.fn>;

  function createComponent(webhooks: PaymentWebhookEventSummary[] = []): void {
    fixture = TestBed.createComponent(PaymentConnectionPage);
    fixture.detectChanges();
    http
      .expectOne('/api/v1/stores/tienda-a/admin/payment-webhooks?status=DEAD')
      .flush(webhooks);
    http.expectOne('/api/v1/stores/tienda-a/settings').flush({
      timezone: 'America/Argentina/Buenos_Aires',
    });
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

  it('renders only the safe operational webhook projection', () => {
    createComponent([DEAD_WEBHOOK]);
    flushConnection(CONNECTED);

    const text = fixture.nativeElement.textContent.replace(/\s+/g, ' ');
    expect(text).toContain('Evento evt-safe-42');
    expect(text).toContain('Reintentos agotados');
    expect(text).toContain('Intentos:8');
    expect(text).toContain('Código:MP_TEMPORARY_FAILURE');
    expect(text).not.toContain('payload');
    expect(text).not.toContain('access_token');
  });

  it('requires confirmation and prevents duplicate webhook retries', () => {
    createComponent([DEAD_WEBHOOK]);
    flushConnection(CONNECTED);

    const retry = fixture.nativeElement.querySelector(
      '[aria-label="Reintentar evento evt-safe-42"]',
    ) as HTMLButtonElement;
    retry.click();
    fixture.detectChanges();
    const dialog = fixture.nativeElement.querySelector('[role="dialog"]') as HTMLElement;
    expect(dialog).toBeTruthy();
    expect(dialog.getAttribute('aria-modal')).toBe('true');
    expect(document.activeElement?.textContent).toContain('Cancelar');

    const confirm = fixture.nativeElement.querySelector(
      '.event-list .confirmation .primary',
    ) as HTMLButtonElement;
    confirm.click();
    confirm.click();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    const retryRequest = http.expectOne(
      '/api/v1/stores/tienda-a/admin/payment-webhooks/evt-safe-42/retry',
    );
    expect(retryRequest.request.method).toBe('POST');
    retryRequest.flush({
      eventId: 'evt-safe-42',
      status: 'RETRY_SCHEDULED',
      scheduledAt: '2026-08-01T12:01:00Z',
    });
    http
      .expectOne('/api/v1/stores/tienda-a/admin/payment-webhooks?status=DEAD')
      .flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('programado para reintento');
    expect(fixture.nativeElement.textContent).toContain(
      'No hay eventos fallidos que requieran atención.',
    );
  });

  it('formats operational dates in the configured store timezone', () => {
    createComponent([DEAD_WEBHOOK]);
    flushConnection(CONNECTED);

    const text = fixture.nativeElement.textContent.replace(/\s+/g, ' ');
    expect(text).toContain('Fecha:1/8/26, 9:00');
    expect(text).toContain('(America/Argentina/Buenos_Aires)');
  });

  it('closes retry confirmation with Escape and restores focus to its trigger', async () => {
    createComponent([DEAD_WEBHOOK]);
    flushConnection(CONNECTED);

    const retry = fixture.nativeElement.querySelector(
      '[aria-label="Reintentar evento evt-safe-42"]',
    ) as HTMLButtonElement;
    retry.focus();
    retry.click();
    fixture.detectChanges();
    const dialog = fixture.nativeElement.querySelector('[role="dialog"]') as HTMLElement;
    dialog.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve));

    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    expect(document.activeElement).toBe(
      fixture.nativeElement.querySelector('[aria-label="Reintentar evento evt-safe-42"]'),
    );
  });

  it('shows an operational error independently from the connection', () => {
    fixture = TestBed.createComponent(PaymentConnectionPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/stores/tienda-a/settings').flush({
      timezone: 'America/Argentina/Buenos_Aires',
    });
    http
      .expectOne('/api/v1/stores/tienda-a/admin/payment-webhooks?status=DEAD')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    flushConnection(CONNECTED);

    const text = fixture.nativeElement.textContent.replace(/\s+/g, ' ');
    expect(text).toContain('No pudimos comunicarnos con el servicio de pagos.');
    expect(text).toContain('Conectada a: TESTABC123');
    expect(fixture.nativeElement.querySelector('.operations [role="alert"]')).toBeTruthy();
  });

  it('cancels tenant-scoped requests before loading another store', () => {
    fixture = TestBed.createComponent(PaymentConnectionPage);
    fixture.detectChanges();
    const storeAConnection = http.expectOne(
      '/api/v1/stores/tienda-a/admin/payment-connection',
    );
    const storeAWebhooks = http.expectOne(
      '/api/v1/stores/tienda-a/admin/payment-webhooks?status=DEAD',
    );
    const storeASettings = http.expectOne('/api/v1/stores/tienda-a/settings');

    storeParams.next(convertToParamMap({ storeSlug: 'tienda-b' }));
    fixture.detectChanges();

    expect(storeAConnection.cancelled).toBe(true);
    expect(storeAWebhooks.cancelled).toBe(true);
    expect(storeASettings.cancelled).toBe(true);
    http.expectOne('/api/v1/stores/tienda-b/admin/payment-connection').flush(NOT_CONNECTED);
    http
      .expectOne('/api/v1/stores/tienda-b/admin/payment-webhooks?status=DEAD')
      .flush([]);
    http.expectOne('/api/v1/stores/tienda-b/settings').flush({
      timezone: 'America/Argentina/Buenos_Aires',
    });
  });

  it('keeps OAuth configuration errors separate from webhook retry success', () => {
    createComponent([DEAD_WEBHOOK]);
    http.expectOne('/api/v1/stores/tienda-a/admin/payment-connection').flush(
      { code: 'PAYMENT_CONNECTION_DISABLED' },
      { status: 503, statusText: 'Unavailable' },
    );
    fixture.detectChanges();

    const retry = fixture.nativeElement.querySelector(
      '[aria-label="Reintentar evento evt-safe-42"]',
    ) as HTMLButtonElement;
    retry.click();
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.event-list .confirmation .primary') as HTMLButtonElement)
      .click();
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http
      .expectOne('/api/v1/stores/tienda-a/admin/payment-webhooks/evt-safe-42/retry')
      .flush({ eventId: 'evt-safe-42', status: 'RETRY_SCHEDULED', scheduledAt: DEAD_WEBHOOK.occurredAt });
    http
      .expectOne('/api/v1/stores/tienda-a/admin/payment-webhooks?status=DEAD')
      .flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('header + .notice')).toBeNull();
    expect(fixture.nativeElement.querySelector('.operations .notice')?.textContent).toContain(
      'programado para reintento',
    );
    expect(fixture.nativeElement.textContent).toContain(
      'La conexión de cuentas de Mercado Pago está deshabilitada en este entorno.',
    );
  });
});
