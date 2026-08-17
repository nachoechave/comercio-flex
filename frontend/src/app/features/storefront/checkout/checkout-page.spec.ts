import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { CsrfService } from '../../../core/auth/csrf.service';
import { CartService } from '../cart/cart.service';
import { CheckoutProNavigationService } from '../payment/checkout-pro-navigation.service';
import { StorefrontApiService } from '../storefront-api.service';
import { StorefrontContextService } from '../storefront-context.service';
import { PublicProductDetail, StoreSettings } from '../storefront.models';
import { CheckoutPage } from './checkout-page';

describe('CheckoutPage', () => {
  let fixture: ComponentFixture<CheckoutPage>;
  let http: HttpTestingController;
  let cart: CartService;
  let router: Router;
  const paymentNavigation = { navigate: vi.fn() };
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
    paymentNavigation.navigate.mockReset();
    await TestBed.configureTestingModule({
      imports: [CheckoutPage],
      providers: [
        StorefrontApiService,
        CsrfService,
        { provide: CheckoutProNavigationService, useValue: paymentNavigation },
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

  it('sends only customer data and cart identifiers, then starts the payment', async () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const component = fixture.componentInstance as unknown as {
      form: {
        setValue(value: {
          customerName: string;
          customerPhone: string;
          customerEmail: string;
          notes: string;
        }): void;
      };
      submit(): void;
    };
    component.form.setValue({
      customerName: 'Ana Pérez',
      customerPhone: '11 5555 1234',
      customerEmail: 'ana@example.com',
      notes: 'Cortado fino',
    });
    component.submit();

    http.expectOne('/api/v1/auth/csrf').flush({});
    const order = http.expectOne('/api/v1/stores/tienda-a/orders');
    expect(order.request.headers.get('Idempotency-Key')).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
    expect(order.request.body).toEqual({
      customerName: 'Ana Pérez',
      customerPhone: '11 5555 1234',
      customerEmail: 'ana@example.com',
      notes: 'Cortado fino',
      items: [{ variantId: 'variant-1', quantity: '2' }],
    });
    expect(order.request.body.items[0].unitPrice).toBeUndefined();
    order.flush({
      order: { id: 'order-1' },
      lookupToken: 'private-token',
      replayed: false,
    });

    const payment = http.expectOne(
      (request) =>
        request.url === '/api/v1/stores/tienda-a/orders/order-1/payments/checkout-pro' &&
        request.params.get('token') === 'private-token',
    );
    expect(payment.request.headers.get('Idempotency-Key')).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
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
    expect(paymentNavigation.navigate).toHaveBeenCalledWith(
      'https://www.mercadopago.com.ar/checkout/v1/redirect',
    );
  });

  it('requires the customer name and a valid email before creating the order', () => {
    const component = fixture.componentInstance as unknown as {
      form: {
        patchValue(value: {
          customerName: string;
          customerPhone: string;
          customerEmail: string;
        }): void;
      };
      submit(): void;
    };
    component.form.patchValue({
      customerName: '   ',
      customerPhone: '11 5555 1234',
      customerEmail: '',
    });

    component.submit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Ingresá tu nombre.');
    expect(fixture.nativeElement.textContent).toContain('Ingresá un correo válido.');
    expect(fixture.nativeElement.textContent).toContain('Revisá los datos de contacto marcados.');
    http.expectNone('/api/v1/auth/csrf');
    http.expectNone('/api/v1/stores/tienda-a/orders');
  });

  it('keeps the idempotency key when the outcome is uncertain', () => {
    const component = fixture.componentInstance as unknown as {
      form: {
        patchValue(value: {
          customerName: string;
          customerPhone: string;
          customerEmail: string;
        }): void;
      };
      submit(): void;
    };
    component.form.patchValue({
      customerName: 'Ana Pérez',
      customerPhone: '11 5555 1234',
      customerEmail: 'ana@example.com',
    });
    component.submit();
    http.expectOne('/api/v1/auth/csrf').flush({});
    const first = http.expectOne('/api/v1/stores/tienda-a/orders');
    const firstKey = first.request.headers.get('Idempotency-Key');
    first.error(new ProgressEvent('network error'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('misma solicitud');

    component.submit();
    const retry = http.expectOne('/api/v1/stores/tienda-a/orders');
    expect(retry.request.headers.get('Idempotency-Key')).toBe(firstKey);
    retry.flush({ detail: 'Todavía no disponible.' }, { status: 503, statusText: 'Unavailable' });
  });
});
