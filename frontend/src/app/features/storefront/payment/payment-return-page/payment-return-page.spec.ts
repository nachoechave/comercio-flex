import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { CheckoutProNavigationService } from '../checkout-pro-navigation.service';
import { PaymentRecoveryService } from '../payment-recovery.service';
import { PaymentReturnStatus } from '../payment.models';
import { PaymentReturnPage } from './payment-return-page';

describe('PaymentReturnPage', () => {
  let fixture: ComponentFixture<PaymentReturnPage>;
  let http: HttpTestingController;
  const navigation = { navigate: vi.fn() };
  const approved: PaymentReturnStatus = {
    orderId: 'order-1',
    orderNumber: 'PED-0001',
    orderStatus: 'CONFIRMED',
    paymentStatus: 'APPROVED',
    returnOutcome: null,
    canRetry: false,
    updatedAt: '2026-08-01T12:00:00Z',
  };

  beforeEach(async () => {
    sessionStorage.clear();
    navigation.navigate.mockReset();
    await TestBed.configureTestingModule({
      imports: [PaymentReturnPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CheckoutProNavigationService, useValue: navigation },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({
                storeSlug: 'tienda-a',
                returnToken: 'opaque-token',
              }),
              queryParamMap: convertToParamMap({ payment_id: '171652320068' }),
            },
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    vi.useFakeTimers();
    fixture = TestBed.createComponent(PaymentReturnPage);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
    fixture.destroy();
    http.verify();
    sessionStorage.clear();
  });

  it('checks immediately, renders an approved result and stops polling', async () => {
    vi.advanceTimersByTime(0);
    reconciliationRequest().flush(approved);
    fixture.detectChanges();
    await Promise.resolve();

    expect(fixture.nativeElement.textContent).toContain('Pago aprobado');
    expect(fixture.nativeElement.textContent).toContain('PED-0001');
    expect(document.activeElement?.id).toBe('payment-result-title');
    vi.advanceTimersByTime(6_000);
    http.expectNone(statusUrl);
  });

  it('polls pending payments every three seconds until a final state', () => {
    vi.advanceTimersByTime(0);
    reconciliationRequest().flush({
      ...approved,
      orderStatus: 'PENDING_CONFIRMATION',
      paymentStatus: 'PENDING',
    });
    vi.advanceTimersByTime(3_000);
    statusRequest().flush({ ...approved, paymentStatus: 'REQUIRES_REVIEW' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Pago en revisión');
    expect(fixture.nativeElement.textContent).toContain('No vuelvas a pagar');
    vi.advanceTimersByTime(6_000);
    http.expectNone(statusUrl);
  });

  it('reconciles the verified provider payment when the customer refreshes', () => {
    vi.advanceTimersByTime(0);
    reconciliationRequest().flush({
      ...approved,
      orderStatus: 'PENDING_CONFIRMATION',
      paymentStatus: 'PENDING',
    });
    fixture.detectChanges();

    (
      fixture.componentInstance as unknown as {
        refresh(): void;
      }
    ).refresh();
    const reconciliation = http.expectOne(
      (request) =>
        request.url === `${statusUrl}/reconcile` && request.params.keys().length === 0,
    );
    expect(reconciliation.request.method).toBe('POST');
    reconciliation.flush(approved);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Pago aprobado');
  });

  it('allows retry only with backend permission and local recovery data', () => {
    TestBed.inject(PaymentRecoveryService).remember(
      'tienda-a',
      'order-1',
      'private-token',
      'idem-1',
    );
    vi.advanceTimersByTime(0);
    reconciliationRequest().flush({ ...approved, paymentStatus: 'REJECTED', canRetry: true });
    fixture.detectChanges();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    expect(button.textContent).toContain('Intentar el pago nuevamente');
    button.click();
    const payment = http.expectOne(
      (request) =>
        request.url.endsWith('/orders/order-1/payments/checkout-pro') &&
        request.params.get('token') === 'private-token',
    );
    expect(payment.request.headers.get('Idempotency-Key')).not.toBe('idem-1');
    expect(payment.request.headers.get('Idempotency-Key')).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
    payment.flush({
      checkoutUrl: 'https://www.mercadopago.com.ar/checkout',
      paymentAttemptId: 'attempt-1',
      expiresAt: '2026-08-01T12:00:00Z',
      replayed: true,
    });
    expect(navigation.navigate).toHaveBeenCalled();
  });

  it('does not offer another payment while provider confirmation is pending', () => {
    TestBed.inject(PaymentRecoveryService).remember(
      'tienda-a',
      'order-1',
      'private-token',
      'idem-1',
    );
    vi.advanceTimersByTime(0);
    reconciliationRequest().flush({
      ...approved,
      orderStatus: 'PENDING_CONFIRMATION',
      paymentStatus: 'PENDING',
      canRetry: true,
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No vuelvas a pagar');
    expect(fixture.nativeElement.textContent).not.toContain('Intentar el pago nuevamente');
  });

  const statusUrl = '/api/v1/stores/tienda-a/payment-returns/opaque-token';

  function statusRequest() {
    return http.expectOne(statusUrl);
  }

  function reconciliationRequest() {
    http.expectOne('/api/v1/auth/csrf').flush({});
    const request = http.expectOne(
      (candidate) =>
        candidate.url === `${statusUrl}/reconcile` && candidate.params.keys().length === 0,
    );
    expect(request.request.method).toBe('POST');
    return request;
  }
});
