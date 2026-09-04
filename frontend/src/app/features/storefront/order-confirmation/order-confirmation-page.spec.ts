import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { CsrfService } from '../../../core/auth/csrf.service';
import { CheckoutProNavigationService } from '../payment/checkout-pro-navigation.service';
import { QrCodeService } from '../payment/qr-code.service';
import { StorefrontApiService } from '../storefront-api.service';
import { StorefrontContextService } from '../storefront-context.service';
import { GuestOrder, StoreSettings } from '../storefront.models';
import { OrderConfirmationPage } from './order-confirmation-page';

describe('OrderConfirmationPage', () => {
  let fixture: ComponentFixture<OrderConfirmationPage>;
  let http: HttpTestingController;
  const orderId = '11111111-1111-4111-8111-111111111111';
  const params = new BehaviorSubject(convertToParamMap({ storeSlug: 'tienda-a', orderId }));
  const queryParams = new BehaviorSubject(
    convertToParamMap({ token: 'private-token', payment: 'not-enabled' }),
  );
  const settings = signal<StoreSettings | null>({
    slug: 'tienda-a',
    storeName: 'Tienda A',
    currencyCode: 'ARS',
    timezone: 'America/Argentina/Buenos_Aires',
  });
  const paymentNavigation = { navigate: vi.fn() };
  const paymentQr = { create: vi.fn() };
  const order: GuestOrder = {
    id: orderId,
    number: 'ORD-000004',
    status: 'PENDING_CONFIRMATION',
    fulfillmentType: 'PICKUP',
    customerName: 'Carlos',
    contactHint: 'n***@gmail.com',
    currencyCode: 'ARS',
    subtotal: '12333.00',
    reservationExpiresAt: '2026-08-17T03:31:00Z',
    createdAt: '2026-08-17T03:01:00Z',
    paymentMethod: 'MERCADO_PAGO',
    listSubtotal: '2500.00',
    discountPercentage: '0.00',
    discountAmount: '0.00',
    items: [
      {
        productId: 'product-1',
        variantId: 'variant-1',
        productName: 'Remera nike',
        size: 'm',
        color: 'rojo',
        unitCode: 'UNIT',
        unitPrice: '12333.00',
        quantity: '1.000',
        lineTotal: '12333.00',
      },
    ],
  };

  beforeEach(async () => {
    vi.useFakeTimers();
    sessionStorage.clear();
    localStorage.clear();
    paymentNavigation.navigate.mockReset();
    paymentQr.create.mockReset();
    paymentQr.create.mockResolvedValue('data:image/png;base64,checkout-qr');
    await TestBed.configureTestingModule({
      imports: [OrderConfirmationPage],
      providers: [
        StorefrontApiService,
        CsrfService,
        { provide: CheckoutProNavigationService, useValue: paymentNavigation },
        { provide: QrCodeService, useValue: paymentQr },
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: StorefrontContextService,
          useValue: { settings },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            pathFromRoot: [{ paramMap: params.asObservable() }],
            queryParamMap: queryParams.asObservable(),
            snapshot: {
              paramMap: convertToParamMap({}),
              queryParamMap: queryParams.value,
            },
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OrderConfirmationPage);
    fixture.detectChanges();
    http
      .expectOne(
        (request) =>
          request.url === `/api/v1/stores/tienda-a/orders/${orderId}` &&
          request.params.get('token') === 'private-token',
      )
      .flush(order);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
    localStorage.clear();
    vi.useRealTimers();
  });

  it('keeps Checkout Pro as same-device navigation and never renders its URL as QR', async () => {
    http.expectOne('/api/v1/stores/tienda-a/payment-methods').flush({
      mercadoPago: true,
      bankTransfer: false,
    });
    http
      .expectOne(
        (request) =>
          request.url === `/api/v1/stores/tienda-a/orders/${orderId}/payments/bank-transfer` &&
          request.params.get('token') === 'private-token',
      )
      .flush({}, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'El pago en línea no está habilitado para esta tienda.',
    );
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(button.textContent).toContain('Pagar con Mercado Pago');

    button.click();

    http.expectOne('/api/v1/auth/csrf').flush({});
    const payment = http.expectOne(
      (request) =>
        request.url === `/api/v1/stores/tienda-a/orders/${orderId}/payments/checkout-pro` &&
        request.params.get('token') === 'private-token',
    );
    expect(payment.request.headers.get('Idempotency-Key')).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
    payment.flush({
      checkoutUrl: 'https://www.mercadopago.com.ar/checkout/v1/redirect',
      paymentAttemptId: 'attempt-1',
      expiresAt: '2026-08-17T03:31:00Z',
      replayed: false,
    });
    await Promise.resolve();
    fixture.detectChanges();

    expect(paymentQr.create).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.checkout-qr')).toBeNull();
    expect(paymentNavigation.navigate).not.toHaveBeenCalled();

    const open = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((candidate) => candidate.textContent?.includes('Abrir Mercado Pago'));
    open?.click();

    expect(paymentNavigation.navigate).toHaveBeenCalledWith(
      'https://www.mercadopago.com.ar/checkout/v1/redirect',
    );
  });

  it('renders provider qr_data and removes the QR when local polling confirms the order', async () => {
    http.expectOne('/api/v1/stores/tienda-a/payment-methods').flush({
      mercadoPago: true,
      mercadoPagoQr: true,
      bankTransfer: false,
    });
    http
      .expectOne(
        (request) =>
          request.url ===
            `/api/v1/stores/tienda-a/orders/${orderId}/payments/bank-transfer` &&
          request.params.get('token') === 'private-token',
      )
      .flush({}, { status: 404, statusText: 'Not Found' });
    http
      .expectOne((request) => request.url.endsWith('/payments/qr'))
      .flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    const mercadoPagoQr = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((candidate) => candidate.textContent?.includes('Pagar con QR'));
    mercadoPagoQr?.click();
    http.expectOne('/api/v1/auth/csrf').flush({});
    http
      .expectOne((request) => request.url.endsWith('/payments/qr'))
      .flush({
        paymentAttemptId: 'attempt-1',
        qrData: 'provider-native-qr-data',
        expiresAt: '2026-08-17T03:31:00Z',
        status: 'PENDING',
        replayed: true,
      });
    await Promise.resolve();
    fixture.detectChanges();
    expect(paymentQr.create).toHaveBeenCalledWith('provider-native-qr-data');
    expect(paymentQr.create).not.toHaveBeenCalledWith(expect.stringContaining('checkout'));
    expect(fixture.nativeElement.querySelector('.checkout-qr')).not.toBeNull();

    await vi.advanceTimersByTimeAsync(12_000);
    http.expectNone((request) => request.url.endsWith('/checkout-pro/reconcile'));
    http
      .expectOne((request) => request.url === `/api/v1/stores/tienda-a/orders/${orderId}`)
      .flush({ ...order, status: 'CONFIRMED' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Pedido confirmado');
    expect(fixture.nativeElement.querySelector('.checkout-qr')).toBeNull();
  });

  it('uses a new idempotency key when switching from Checkout Pro to QR', async () => {
    http.expectOne('/api/v1/stores/tienda-a/payment-methods').flush({
      mercadoPago: true,
      mercadoPagoQr: true,
      bankTransfer: false,
    });
    http
      .expectOne((request) => request.url.endsWith('/payments/bank-transfer'))
      .flush({}, { status: 404, statusText: 'Not Found' });
    http
      .expectOne((request) => request.url.endsWith('/payments/qr'))
      .flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    const checkoutButton = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((candidate) => candidate.textContent?.includes('Pagar con Mercado Pago'));
    checkoutButton?.click();
    http.expectOne('/api/v1/auth/csrf').flush({});
    const checkoutRequest = http.expectOne((request) => request.url.endsWith('/checkout-pro'));
    const checkoutIdempotencyKey = checkoutRequest.request.headers.get('Idempotency-Key');
    checkoutRequest.flush({
      checkoutUrl: 'https://www.mercadopago.com.ar/checkout/v1/redirect',
      paymentAttemptId: 'checkout-attempt',
      expiresAt: '2026-08-17T03:31:00Z',
      replayed: false,
    });
    fixture.detectChanges();

    const chooseAnother = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((candidate) => candidate.textContent?.includes('Elegir otro medio de pago'));
    chooseAnother?.click();
    fixture.detectChanges();

    const qrButton = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((candidate) => candidate.textContent?.includes('Pagar con QR'));
    qrButton?.click();
    const qrRequest = http.expectOne((request) => request.url.endsWith('/payments/qr'));
    const qrIdempotencyKey = qrRequest.request.headers.get('Idempotency-Key');
    expect(qrIdempotencyKey).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
    expect(qrIdempotencyKey).not.toBe(checkoutIdempotencyKey);
    qrRequest.flush({
      paymentAttemptId: 'qr-attempt',
      qrData: 'provider-native-qr-data',
      expiresAt: '2026-08-17T03:31:00Z',
      status: 'PENDING',
      replayed: false,
    });
  });

  it('fails closed and allows retrying when payment methods cannot be loaded', () => {
    http
      .expectOne('/api/v1/stores/tienda-a/payment-methods')
      .flush({}, { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'No pudimos consultar los medios de pago. Intentá nuevamente.',
    );
    const initialLabels = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).map((button) => button.textContent ?? '');
    expect(initialLabels.some((label) => label.includes('Mercado Pago'))).toBe(false);
    expect(initialLabels.some((label) => label.includes('Transferencia bancaria'))).toBe(false);

    const retry = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((button) => button.textContent?.includes('Reintentar consulta'));
    expect(retry).toBeDefined();
    retry?.click();

    http.expectOne('/api/v1/stores/tienda-a/payment-methods').flush({
      mercadoPago: false,
      bankTransfer: true,
    });
    http
      .expectOne(
        (request) =>
          request.url === `/api/v1/stores/tienda-a/orders/${orderId}/payments/bank-transfer` &&
          request.params.get('token') === 'private-token',
      )
      .flush({}, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain(
      'No pudimos consultar los medios de pago. Intentá nuevamente.',
    );
    expect(fixture.nativeElement.textContent).toContain(
      'Mercado Pago no está disponible en este momento.',
    );
    expect(fixture.nativeElement.textContent).not.toContain(
      'Transferencia bancaria',
    );
    expect(fixture.nativeElement.textContent).not.toContain(
      'Tarjetas y dinero disponible',
    );
  });

  it('keeps an existing bank transfer usable after new transfers are disabled', () => {
    http.expectOne('/api/v1/stores/tienda-a/payment-methods').flush({
      mercadoPago: false,
      bankTransfer: false,
    });
    http
      .expectOne(
        (request) =>
          request.url === `/api/v1/stores/tienda-a/orders/${orderId}/payments/bank-transfer` &&
          request.params.get('token') === 'private-token',
      )
      .flush({
        id: '22222222-2222-4222-8222-222222222222',
        orderId,
        orderNumber: 'ORD-000004',
        attemptNumber: 1,
        status: 'AWAITING_RECEIPT',
        bankName: 'Banco Demo',
        accountHolder: 'Tienda A SA',
        alias: 'TIENDA.A',
        cbuCvu: null,
        amount: '12333.00',
        currencyCode: 'ARS',
        reservationExpiresAt: '2026-08-25T03:31:00Z',
        receiptUploadedAt: null,
        rejectionReason: null,
        canUpload: true,
        updatedAt: '2026-08-24T03:31:00Z',
      });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Transferencia bancaria');
    expect(fixture.nativeElement.textContent).toContain('Subir comprobante');
    expect(fixture.nativeElement.textContent).not.toContain(
      'El comercio todavía no habilitó un medio de pago.',
    );
  });

  it('updates UNDER_REVIEW to APPROVED without refreshing the page', async () => {
    flushPaymentMethodsAndTransfer(bankTransfer('UNDER_REVIEW'));

    await vi.advanceTimersByTimeAsync(12_000);
    http.expectNone((request) => request.url.endsWith('/checkout-pro/reconcile'));
    http
      .expectOne((request) => request.url === `/api/v1/stores/tienda-a/orders/${orderId}`)
      .flush({ ...order, status: 'CONFIRMED' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Pedido confirmado');
    expect(fixture.nativeElement.textContent).not.toContain('Subir comprobante');
  });

  it('updates UNDER_REVIEW to REJECTED and allows a new receipt', async () => {
    flushPaymentMethodsAndTransfer(bankTransfer('UNDER_REVIEW'));

    await vi.advanceTimersByTimeAsync(12_000);
    http.expectNone((request) => request.url.endsWith('/checkout-pro/reconcile'));
    http
      .expectOne((request) => request.url === `/api/v1/stores/tienda-a/orders/${orderId}`)
      .flush(order);
    http
      .expectOne((request) => request.url.endsWith('/payments/bank-transfer'))
      .flush(bankTransfer('REJECTED'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('El comprobante fue rechazado');
    expect(fixture.nativeElement.textContent).toContain('No se distingue el importe');
    expect(fixture.nativeElement.textContent).toContain('Enviar un nuevo comprobante');
  });

  it('cancels polling on destroy and does not overlap slow requests', async () => {
    flushPaymentMethodsAndTransfer(bankTransfer('UNDER_REVIEW'));

    await vi.advanceTimersByTimeAsync(12_000);
    http.expectNone((request) => request.url.endsWith('/checkout-pro/reconcile'));
    const slow = http.expectOne(
      (request) => request.url === `/api/v1/stores/tienda-a/orders/${orderId}`,
    );
    await vi.advanceTimersByTimeAsync(24_000);
    http.expectNone((request) => request.url === `/api/v1/stores/tienda-a/orders/${orderId}`);
    slow.flush(order);
    http
      .expectOne((request) => request.url.endsWith('/payments/bank-transfer'))
      .flush(bankTransfer('UNDER_REVIEW'));

    fixture.destroy();
    await vi.advanceTimersByTimeAsync(24_000);
    http.expectNone((request) => request.url === `/api/v1/stores/tienda-a/orders/${orderId}`);
  });

  it('shows one clear success message after uploading a receipt', () => {
    flushPaymentMethodsAndTransfer(bankTransfer('AWAITING_RECEIPT'));
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[type="file"]');
    const file = new File(['%PDF-1.4\n%%EOF'], 'comprobante.pdf', { type: 'application/pdf' });
    Object.defineProperty(input, 'files', { value: [file] });
    input.dispatchEvent(new Event('change'));

    http
      .expectOne((request) => request.url.endsWith('/receipt'))
      .flush(bankTransfer('UNDER_REVIEW'));
    fixture.detectChanges();

    expect((fixture.nativeElement.textContent.match(/Comprobante enviado/g) ?? []).length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain(
      'El comercio está revisando tu transferencia.',
    );
  });

  function flushPaymentMethodsAndTransfer(transfer: ReturnType<typeof bankTransfer>): void {
    http.expectOne('/api/v1/stores/tienda-a/payment-methods').flush({
      mercadoPago: true,
      bankTransfer: true,
    });
    http.expectOne((request) => request.url.endsWith('/payments/bank-transfer')).flush(transfer);
    fixture.detectChanges();
  }

  function bankTransfer(status: 'AWAITING_RECEIPT' | 'UNDER_REVIEW' | 'REJECTED') {
    return {
      id: '22222222-2222-4222-8222-222222222222',
      orderId,
      orderNumber: 'ORD-000004',
      attemptNumber: 1,
      status,
      bankName: 'Banco Demo',
      accountHolder: 'Tienda A SA',
      alias: 'TIENDA.A',
      cbuCvu: null,
      amount: '12333.00',
      currencyCode: 'ARS',
      reservationExpiresAt: '2026-08-25T18:31:00Z',
      receiptUploadedAt: status === 'AWAITING_RECEIPT' ? null : '2026-08-25T15:00:00Z',
      rejectionReason: status === 'REJECTED' ? 'No se distingue el importe' : null,
      canUpload: status !== 'UNDER_REVIEW',
      updatedAt: '2026-08-25T15:00:00Z',
    };
  }
});
