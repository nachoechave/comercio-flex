import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { StorefrontApiService } from '../storefront-api.service';
import { GuestOrder } from '../storefront.models';
import { GuestOrderHistoryService } from './guest-order-history.service';
import { RecentOrdersPage } from './recent-orders-page';

describe('RecentOrdersPage', () => {
  let fixture: ComponentFixture<RecentOrdersPage>;
  let history: GuestOrderHistoryService;
  let http: HttpTestingController;
  let router: Router;
  const params = new BehaviorSubject(convertToParamMap({ storeSlug: 'tienda-a' }));
  const token = 'private-lookup-token-1234567890';
  const order: GuestOrder = {
    id: '11111111-1111-4111-8111-111111111111',
    number: 'ORD-000011',
    status: 'PENDING_CONFIRMATION',
    fulfillmentType: 'PICKUP',
    customerName: 'Ana',
    contactHint: 'a***@mail.com',
    currencyCode: 'ARS',
    subtotal: '19999.00',
    reservationExpiresAt: '2026-08-26T12:00:00Z',
    createdAt: '2026-08-25T12:00:00Z',
    paymentMethod: 'MERCADO_PAGO',
    listSubtotal: '2500.00',
    discountPercentage: '0.00',
    discountAmount: '0.00',
    items: [],
  };

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [RecentOrdersPage],
      providers: [
        StorefrontApiService,
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            pathFromRoot: [{ paramMap: params.asObservable() }],
            snapshot: { paramMap: convertToParamMap({}) },
          },
        },
      ],
    }).compileComponents();
    history = TestBed.inject(GuestOrderHistoryService);
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('shows only this browser and store data, refreshed from the backend, without exposing its token', () => {
    history.remember('tienda-a', order, token);
    history.remember('tienda-b', { ...order, id: '22222222-2222-4222-8222-222222222222' }, token);
    fixture = TestBed.createComponent(RecentOrdersPage);
    fixture.detectChanges();

    http.expectOne((request) =>
      request.url === `/api/v1/stores/tienda-a/orders/${order.id}` &&
      request.params.get('token') === token,
    ).flush({ ...order, status: 'CONFIRMED' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('ORD-000011');
    expect(fixture.nativeElement.textContent).toContain('Confirmado');
    expect(fixture.nativeElement.textContent).not.toContain(token);
    expect(fixture.nativeElement.innerHTML).not.toContain(token);
    expect(fixture.nativeElement.textContent).not.toContain('22222222-2222-4222-8222-222222222222');
  });

  it('removes a local entry when its private lookup is no longer valid', () => {
    history.remember('tienda-a', order, token);
    fixture = TestBed.createComponent(RecentOrdersPage);
    fixture.detectChanges();

    http.expectOne((request) => request.url.endsWith(`/orders/${order.id}`))
      .flush({}, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(history.list('tienda-a')).toEqual([]);
    expect(fixture.nativeElement.textContent).toContain(
      'No tenés pedidos recientes en este dispositivo.',
    );
  });

  it('opens a stored order without adding the lookup token to the URL', async () => {
    history.remember('tienda-a', order, token);
    fixture = TestBed.createComponent(RecentOrdersPage);
    fixture.detectChanges();
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    button.click();

    expect(navigate).toHaveBeenCalledWith([
      '/tiendas', 'tienda-a', 'pedidos', order.id,
    ]);
    http.expectOne((request) => request.url.endsWith(`/orders/${order.id}`))
      .flush(order);
  });
});
