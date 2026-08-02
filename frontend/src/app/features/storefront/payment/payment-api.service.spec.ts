import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PaymentApiService } from './payment-api.service';

describe('PaymentApiService', () => {
  let api: PaymentApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(PaymentApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts Checkout Pro with the private token and idempotency key', () => {
    api.startCheckout('tienda a', 'order/1', 'private token', 'idem-1').subscribe();

    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/stores/tienda%20a/orders/order%2F1/payments/checkout-pro' &&
        candidate.params.get('token') === 'private token',
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    expect(request.request.headers.get('Idempotency-Key')).toBe('idem-1');
    request.flush({
      checkoutUrl: 'https://www.mercadopago.com.ar/checkout',
      paymentAttemptId: 'attempt-1',
      expiresAt: '2026-08-01T12:00:00Z',
      replayed: false,
    });
  });

  it('reads payment status using only the opaque return token', () => {
    api.getReturnStatus('tienda a', 'return/token').subscribe();

    const request = http.expectOne('/api/v1/stores/tienda%20a/payment-returns/return%2Ftoken');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);
    request.flush({
      orderId: 'order-1',
      orderNumber: 'PED-1',
      orderStatus: 'CONFIRMED',
      paymentStatus: 'APPROVED',
      canRetry: false,
      updatedAt: '2026-08-01T12:00:00Z',
    });
  });

  it('asks the backend to reconcile a provider payment securely', () => {
    api.reconcileReturn('tienda a', 'return/token', '171652320068').subscribe();

    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/stores/tienda%20a/payment-returns/return%2Ftoken/reconcile' &&
        candidate.params.get('paymentId') === '171652320068',
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({
      orderId: 'order-1',
      orderNumber: 'PED-1',
      orderStatus: 'CONFIRMED',
      paymentStatus: 'APPROVED',
      canRetry: false,
      updatedAt: '2026-08-01T12:00:00Z',
    });
  });
});
