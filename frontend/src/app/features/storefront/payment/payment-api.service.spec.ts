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
      returnOutcome: null,
      canRetry: false,
      updatedAt: '2026-08-01T12:00:00Z',
    });
  });

  it('reconciles a pending checkout using the private order token', () => {
    api.reconcilePendingCheckout('tienda a', 'order/1', 'private token').subscribe();

    const request = http.expectOne(
      (candidate) =>
        candidate.url ===
          '/api/v1/stores/tienda%20a/orders/order%2F1/payments/checkout-pro/reconcile' &&
        candidate.params.get('token') === 'private token',
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('creates and reloads a native QR order without calling Mercado Pago directly', () => {
    api.startQrOrder('tienda a', 'order/1', 'private token', 'idem-qr').subscribe();
    const start = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/stores/tienda%20a/orders/order%2F1/payments/qr' &&
        candidate.params.get('token') === 'private token',
    );
    expect(start.request.method).toBe('POST');
    expect(start.request.headers.get('Idempotency-Key')).toBe('idem-qr');
    start.flush({
      paymentAttemptId: 'attempt-qr',
      qrData: 'provider-qr-data',
      expiresAt: '2026-08-01T12:00:00Z',
      status: 'PENDING',
      replayed: false,
    });

    api.getCurrentQrOrder('tienda a', 'order/1', 'private token').subscribe();
    const current = http.expectOne(
      (candidate) => candidate.url.endsWith('/payments/qr') &&
        candidate.params.get('token') === 'private token',
    );
    expect(current.request.method).toBe('GET');
    current.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('queries enabled methods and starts a private bank transfer', () => {
    api.getMethods('tienda a').subscribe();
    const methods = http.expectOne('/api/v1/stores/tienda%20a/payment-methods');
    expect(methods.request.method).toBe('GET');
    methods.flush({ mercadoPago: true, mercadoPagoQr: true, bankTransfer: true });

    api.startBankTransfer('tienda a', 'order/1', 'private token').subscribe();
    const start = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/stores/tienda%20a/orders/order%2F1/payments/bank-transfer' &&
        candidate.params.get('token') === 'private token',
    );
    expect(start.request.method).toBe('POST');
    expect(start.request.body).toEqual({});
    start.flush({});
  });

  it('uploads a bank receipt as multipart without setting a public object key', () => {
    const file = new File(['%PDF-1.4\n%%EOF'], 'receipt.pdf', { type: 'application/pdf' });
    api.uploadBankTransferReceipt(
      'tienda a', 'order/1', 'payment/1', 'private token', file,
    ).subscribe();

    const request = http.expectOne(
      (candidate) =>
        candidate.url ===
          '/api/v1/stores/tienda%20a/orders/order%2F1/payments/bank-transfer/payment%2F1/receipt' &&
        candidate.params.get('token') === 'private token',
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeInstanceOf(FormData);
    const uploaded = (request.request.body as FormData).get('file') as File;
    expect(uploaded.name).toBe(file.name);
    expect(uploaded.type).toBe(file.type);
    expect(uploaded.size).toBe(file.size);
    request.flush({});
  });

  it('asks the backend to reconcile a provider payment securely', () => {
    api.reconcileReturn('tienda a', 'return/token').subscribe();

    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/stores/tienda%20a/payment-returns/return%2Ftoken/reconcile' &&
        candidate.params.keys().length === 0,
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({
      orderId: 'order-1',
      orderNumber: 'PED-1',
      orderStatus: 'CONFIRMED',
      paymentStatus: 'APPROVED',
      returnOutcome: null,
      canRetry: false,
      updatedAt: '2026-08-01T12:00:00Z',
    });
  });

  it('asks the backend to inspect a provider return without trusting browser status', () => {
    api.inspectReturn('tienda a', 'return/token').subscribe();

    const request = http.expectOne(
      '/api/v1/stores/tienda%20a/payment-returns/return%2Ftoken/inspect',
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({
      orderId: 'order-1',
      orderNumber: 'PED-1',
      orderStatus: 'PENDING_CONFIRMATION',
      paymentStatus: 'PENDING',
      returnOutcome: 'PAYMENT_NOT_RECORDED',
      canRetry: true,
      updatedAt: '2026-08-01T12:00:00Z',
    });
  });
});
