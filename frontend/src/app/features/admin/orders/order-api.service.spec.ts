import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { OrderApiService } from './order-api.service';

describe('OrderApiService', () => {
  let service: OrderApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OrderApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists orders with pagination and optional filters', () => {
    service.list('tienda-a', 2, 20, 'ORD-12', 'CONFIRMED').subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/stores/tienda-a/admin/orders' &&
        candidate.params.get('page') === '2',
    );
    expect(request.request.params.get('q')).toBe('ORD-12');
    expect(request.request.params.get('status')).toBe('CONFIRMED');
    request.flush({ items: [], page: 2, size: 20, totalItems: 0, totalPages: 0 });
  });

  it('loads a tenant-scoped order', () => {
    service.get('tienda-a', 'order-1').subscribe();
    http.expectOne('/api/v1/stores/tienda-a/admin/orders/order-1').flush({ id: 'order-1' });
  });

  it('sends a transition with the idempotency header', () => {
    service
      .transition(
        'tienda-a',
        'order-1',
        '11111111-1111-4111-8111-111111111111',
        'CONFIRMED',
        'Stock revisado',
      )
      .subscribe();
    const request = http.expectOne('/api/v1/stores/tienda-a/admin/orders/order-1/transitions');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBe(
      '11111111-1111-4111-8111-111111111111',
    );
    expect(request.request.body).toEqual({
      targetStatus: 'CONFIRMED',
      note: 'Stock revisado',
    });
    request.flush({});
  });
});
