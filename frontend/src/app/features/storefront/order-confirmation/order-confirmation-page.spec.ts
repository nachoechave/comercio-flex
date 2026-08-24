import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { CsrfService } from '../../../core/auth/csrf.service';
import { CheckoutProNavigationService } from '../payment/checkout-pro-navigation.service';
import { StorefrontApiService } from '../storefront-api.service';
import { StorefrontContextService } from '../storefront-context.service';
import { GuestOrder, StoreSettings } from '../storefront.models';
import { OrderConfirmationPage } from './order-confirmation-page';

describe('OrderConfirmationPage', () => {
  let fixture: ComponentFixture<OrderConfirmationPage>;
  let http: HttpTestingController;
  const orderId = '11111111-1111-4111-8111-111111111111';
  const params = new BehaviorSubject(
    convertToParamMap({ storeSlug: 'tienda-a', orderId }),
  );
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
    sessionStorage.clear();
    paymentNavigation.navigate.mockReset();
    await TestBed.configureTestingModule({
      imports: [OrderConfirmationPage],
      providers: [
        StorefrontApiService,
        CsrfService,
        { provide: CheckoutProNavigationService, useValue: paymentNavigation },
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
  });

  it('allows retrying when the previous attempt reported payments not enabled', () => {
    http.expectOne('/api/v1/stores/tienda-a/payment-methods').flush({
      mercadoPago: true,
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
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'El pago en línea no está habilitado para esta tienda.',
    );
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(button.textContent).toContain('Reintentar con Mercado Pago');

    button.click();

    http.expectOne('/api/v1/auth/csrf').flush({});
    const payment = http.expectOne(
      (request) =>
        request.url ===
          `/api/v1/stores/tienda-a/orders/${orderId}/payments/checkout-pro` &&
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

    expect(paymentNavigation.navigate).toHaveBeenCalledWith(
      'https://www.mercadopago.com.ar/checkout/v1/redirect',
    );
  });

  it('fails closed and allows retrying when payment methods cannot be loaded', () => {
    http.expectOne('/api/v1/stores/tienda-a/payment-methods').flush(
      {},
      { status: 503, statusText: 'Service Unavailable' },
    );
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
          request.url ===
            `/api/v1/stores/tienda-a/orders/${orderId}/payments/bank-transfer` &&
          request.params.get('token') === 'private-token',
      )
      .flush({}, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain(
      'No pudimos consultar los medios de pago. Intentá nuevamente.',
    );
    expect(fixture.nativeElement.textContent).toContain('Transferencia bancaria');
    expect(fixture.nativeElement.textContent).not.toContain('Tarjetas y dinero disponible');
  });

  it('keeps an existing bank transfer usable after new transfers are disabled', () => {
    http.expectOne('/api/v1/stores/tienda-a/payment-methods').flush({
      mercadoPago: false,
      bankTransfer: false,
    });
    http
      .expectOne(
        (request) =>
          request.url ===
            `/api/v1/stores/tienda-a/orders/${orderId}/payments/bank-transfer` &&
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
});
