import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { CsrfService } from '../../../core/auth/csrf.service';
import { CartService } from '../cart/cart.service';
import { CheckoutProHandoffService } from '../payment/checkout-pro-handoff.service';
import { BankTransferPayment, PaymentMethods } from '../payment/payment.models';
import { StorefrontApiService } from '../storefront-api.service';
import { StorefrontContextService } from '../storefront-context.service';
import { PublicProductDetail, StoreSettings } from '../storefront.models';
import { CheckoutPage } from './checkout-page';

type CheckoutComponentAccess = {
  form: {
    setValue(value: {
      customerName: string;
      customerPhone: string;
      customerEmail: string;
      notes: string;
    }): void;
    patchValue(value: {
      customerName?: string;
      customerPhone?: string;
      customerEmail?: string;
      notes?: string;
    }): void;
  };
  retryPaymentMethods(): void;
  selectPaymentMethod(method: 'MERCADO_PAGO' | 'BANK_TRANSFER'): void;
  selectedPaymentMethod(): 'MERCADO_PAGO' | 'BANK_TRANSFER' | null;
  submit(): void;
};

describe('CheckoutPage', () => {
  let fixture: ComponentFixture<CheckoutPage>;
  let http: HttpTestingController;
  let cart: CartService;
  let router: Router;
  let paymentHandoff: CheckoutProHandoffService;
  const params = new BehaviorSubject(convertToParamMap({ storeSlug: 'tienda-a' }));
  const settings = signal<StoreSettings | null>({
    slug: 'tienda-a',
    storeName: 'Tienda A',
    currencyCode: 'ARS',
    timezone: 'America/Argentina/Buenos_Aires',
  });
  const product: PublicProductDetail = {
    image: null,
    id: 'product-1',
    name: 'Asado',
    slug: 'asado',
    description: null,
    category: { id: 'category-1', name: 'Carnes', slug: 'carnes' },
    variants: [
      {
        id: 'variant-1',
        price: '2500.00',
        size: null,
        color: null,
        available: true,
      },
    ],
  };

  beforeEach(async () => {
    localStorage.clear();
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [CheckoutPage],
      providers: [
        StorefrontApiService,
        CsrfService,
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: StorefrontContextService,
          useValue: {
            settings,
            currencyCode: computed(() => settings()?.currencyCode ?? 'ARS'),
          },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            pathFromRoot: [{ paramMap: params.asObservable() }],
            snapshot: {
              paramMap: convertToParamMap({}),
              queryParamMap: convertToParamMap({}),
            },
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    cart = TestBed.inject(CartService);
    paymentHandoff = TestBed.inject(CheckoutProHandoffService);
    router = TestBed.inject(Router);
    cart.add('tienda-a', { product, variant: product.variants[0], quantity: 2 });
    fixture = TestBed.createComponent(CheckoutPage);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('shows both enabled methods and requires an explicit selection', () => {
    respondMethods({ mercadoPago: true, bankTransfer: true });

    const radios = paymentRadios();
    expect(fixture.nativeElement.textContent).toContain('¿Cómo querés pagar?');
    expect(fixture.nativeElement.textContent).toContain('Mercado Pago');
    expect(fixture.nativeElement.textContent).toContain('Tarjetas y dinero disponible');
    expect(fixture.nativeElement.textContent).toContain('Transferencia bancaria');
    expect(fixture.nativeElement.textContent).toContain('Transferí y enviá el comprobante');
    expect(radios.map((radio) => radio.value)).toEqual(['MERCADO_PAGO', 'BANK_TRANSFER']);
    expect(radios.every((radio) => !radio.checked)).toBe(true);
    expect(submitButton().disabled).toBe(true);
  });

  it('shows and selects only Mercado Pago when it is the sole enabled method', () => {
    respondMethods({ mercadoPago: true, bankTransfer: false });

    const radios = paymentRadios();
    expect(radios).toHaveLength(1);
    expect(radios[0].value).toBe('MERCADO_PAGO');
    expect(radios[0].checked).toBe(true);
    expect(fixture.nativeElement.textContent).not.toContain('Transferencia bancaria');
    expect(submitButton().textContent).toContain('Continuar a Mercado Pago');
    expect(submitButton().disabled).toBe(false);
  });

  it('shows and selects only bank transfer when it is the sole enabled method', () => {
    respondMethods({ mercadoPago: false, bankTransfer: true });

    const radios = paymentRadios();
    expect(radios).toHaveLength(1);
    expect(radios[0].value).toBe('BANK_TRANSFER');
    expect(radios[0].checked).toBe(true);
    expect(fixture.nativeElement.textContent).not.toContain('Mercado Pago');
    expect(submitButton().textContent).toContain('Confirmar pedido y pagar por transferencia');
    expect(submitButton().disabled).toBe(false);
  });

  it('fails closed when no payment method is enabled', () => {
    respondMethods({ mercadoPago: false, bankTransfer: false });

    expect(paymentRadios()).toHaveLength(0);
    expect(fixture.nativeElement.textContent).toContain(
      'El comercio no tiene medios de pago habilitados en este momento.',
    );
    expect(submitButton().disabled).toBe(true);

    component().submit();
    http.expectNone('/api/v1/auth/csrf');
    http.expectNone('/api/v1/stores/tienda-a/orders');
  });

  it('fails closed and allows retry when payment-methods fails', () => {
    const methods = http.expectOne('/api/v1/stores/tienda-a/payment-methods');
    methods.flush({ detail: 'Unavailable' }, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();

    expect(paymentRadios()).toHaveLength(0);
    expect(fixture.nativeElement.textContent).toContain(
      'No pudimos consultar los medios de pago. Intentá nuevamente.',
    );
    expect(submitButton().disabled).toBe(true);

    const retry = fixture.nativeElement.querySelector(
      '.payment-method-status button',
    ) as HTMLButtonElement;
    retry.click();
    respondMethods({ mercadoPago: false, bankTransfer: true });

    expect(component().selectedPaymentMethod()).toBe('BANK_TRANSFER');
    expect(submitButton().disabled).toBe(false);
  });

  it('creates the order and starts Checkout Pro when Mercado Pago is selected', async () => {
    respondMethods({ mercadoPago: true, bankTransfer: true });
    component().selectPaymentMethod('MERCADO_PAGO');
    fillValidForm();
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    component().submit();
    http.expectOne('/api/v1/auth/csrf').flush({});
    const order = expectOrderRequest();
    const orderKey = order.request.headers.get('Idempotency-Key');
    expect(orderKey).toMatch(uuidPattern());
    expect(order.request.body).toEqual({
      customerName: 'Ana Pérez',
      customerPhone: '11 5555 1234',
      customerEmail: 'ana@example.com',
      notes: 'Cortado fino',
      items: [{ variantId: 'variant-1', quantity: '2' }],
    });
    expect(order.request.body.items[0].unitPrice).toBeUndefined();
    flushCreatedOrder(order);

    expect(JSON.parse(localStorage.getItem('comercioflex:guest-orders:v1')!)[0]).toEqual(
      expect.objectContaining({
        storeSlug: 'tienda-a',
        orderId: 'order-1',
        lookupToken: 'private-token',
      }),
    );
    expect(cart.items('tienda-a')).toEqual([]);

    const payment = http.expectOne(
      (request) =>
        request.url === '/api/v1/stores/tienda-a/orders/order-1/payments/checkout-pro' &&
        request.params.get('token') === 'private-token',
    );
    expect(payment.request.method).toBe('POST');
    expect(payment.request.headers.get('Idempotency-Key')).toMatch(uuidPattern());
    payment.flush({
      checkoutUrl: 'https://www.mercadopago.com.ar/checkout/v1/redirect',
      paymentAttemptId: 'attempt-1',
      expiresAt: '2026-08-01T12:00:00Z',
      replayed: false,
    });
    await Promise.resolve();

    expect(cart.items('tienda-a')).toEqual([]);
    expect(navigate).toHaveBeenCalledWith(['/tiendas', 'tienda-a', 'pedidos', 'order-1'], {
      queryParams: { token: 'private-token' },
      replaceUrl: true,
    });
    expect(paymentHandoff.find('tienda-a', 'order-1')).toEqual({
      checkoutUrl: 'https://www.mercadopago.com.ar/checkout/v1/redirect',
      paymentAttemptId: 'attempt-1',
      expiresAt: '2026-08-01T12:00:00Z',
      replayed: false,
    });
  });

  it('creates the order and starts bank transfer without redirecting to Mercado Pago', async () => {
    respondMethods({ mercadoPago: true, bankTransfer: true });
    component().selectPaymentMethod('BANK_TRANSFER');
    fillValidForm();
    fixture.detectChanges();
    expect(submitButton().textContent).toContain('Confirmar pedido y pagar por transferencia');
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    component().submit();
    http.expectOne('/api/v1/auth/csrf').flush({});
    const order = expectOrderRequest();
    flushCreatedOrder(order);

    expect(JSON.parse(localStorage.getItem('comercioflex:guest-orders:v1')!)[0]).toEqual(
      expect.objectContaining({
        storeSlug: 'tienda-a',
        orderId: 'order-1',
        lookupToken: 'private-token',
      }),
    );

    const bankTransfer = http.expectOne(
      (request) =>
        request.url === '/api/v1/stores/tienda-a/orders/order-1/payments/bank-transfer' &&
        request.params.get('token') === 'private-token',
    );
    expect(bankTransfer.request.method).toBe('POST');
    http.expectNone('/api/v1/stores/tienda-a/orders/order-1/payments/checkout-pro');
    bankTransfer.flush(bankTransferResponse());
    await Promise.resolve();

    expect(cart.items('tienda-a')).toEqual([]);
    expect(navigate).toHaveBeenCalledWith(['/tiendas', 'tienda-a', 'pedidos', 'order-1'], {
      queryParams: { token: 'private-token' },
      replaceUrl: true,
    });
    expect(paymentHandoff.find('tienda-a', 'order-1')).toBeNull();
  });

  it('requires the customer name and a valid email before creating the order', () => {
    respondMethods({ mercadoPago: true, bankTransfer: false });
    component().form.patchValue({
      customerName: '   ',
      customerPhone: '11 5555 1234',
      customerEmail: '',
    });

    component().submit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Ingresá tu nombre.');
    expect(fixture.nativeElement.textContent).toContain('Ingresá un correo válido.');
    expect(fixture.nativeElement.textContent).toContain('Revisá los datos de contacto marcados.');
    http.expectNone('/api/v1/auth/csrf');
    http.expectNone('/api/v1/stores/tienda-a/orders');
  });

  it('keeps the order idempotency key when the outcome is uncertain', () => {
    respondMethods({ mercadoPago: true, bankTransfer: false });
    fillValidForm();
    component().submit();
    http.expectOne('/api/v1/auth/csrf').flush({});
    const first = expectOrderRequest();
    const firstKey = first.request.headers.get('Idempotency-Key');
    first.error(new ProgressEvent('network error'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('misma solicitud');

    component().submit();
    const retry = expectOrderRequest();
    expect(retry.request.headers.get('Idempotency-Key')).toBe(firstKey);
    retry.flush({ detail: 'Todavía no disponible.' }, { status: 503, statusText: 'Unavailable' });
  });

  function component(): CheckoutComponentAccess {
    return fixture.componentInstance as unknown as CheckoutComponentAccess;
  }

  function respondMethods(methods: PaymentMethods): void {
    const request = http.expectOne('/api/v1/stores/tienda-a/payment-methods');
    expect(request.request.method).toBe('GET');
    request.flush(methods);
    fixture.detectChanges();
  }

  function paymentRadios(): HTMLInputElement[] {
    return Array.from(
      fixture.nativeElement.querySelectorAll('input[name="paymentMethod"]'),
    ) as HTMLInputElement[];
  }

  function submitButton(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
  }

  function fillValidForm(): void {
    component().form.setValue({
      customerName: 'Ana Pérez',
      customerPhone: '11 5555 1234',
      customerEmail: 'ana@example.com',
      notes: 'Cortado fino',
    });
  }

  function expectOrderRequest() {
    return http.expectOne('/api/v1/stores/tienda-a/orders');
  }

  function flushCreatedOrder(order: ReturnType<typeof expectOrderRequest>): void {
    order.flush({
      order: {
        id: 'order-1',
        number: 'ORD-000011',
        status: 'PENDING_CONFIRMATION',
        fulfillmentType: 'PICKUP',
        customerName: 'Ana Pérez',
        contactHint: 'a***@example.com',
        currencyCode: 'ARS',
        subtotal: '5000.00',
        reservationExpiresAt: '2026-08-01T13:00:00Z',
        createdAt: '2026-08-01T12:00:00Z',
        items: [],
      },
      lookupToken: 'private-token',
      replayed: false,
    });
  }

  function uuidPattern(): RegExp {
    return /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
  }

  function bankTransferResponse(): BankTransferPayment {
    return {
      id: 'transfer-1',
      orderId: 'order-1',
      orderNumber: 'PED-0001',
      attemptNumber: 1,
      status: 'AWAITING_RECEIPT',
      bankName: 'Banco Nación',
      accountHolder: 'Tienda A SA',
      alias: 'TIENDA.A',
      cbuCvu: '0000000000000000000000',
      amount: '5000.00',
      currencyCode: 'ARS',
      reservationExpiresAt: '2026-08-25T12:00:00Z',
      receiptUploadedAt: null,
      rejectionReason: null,
      canUpload: true,
      updatedAt: '2026-08-24T12:00:00Z',
    };
  }
});
