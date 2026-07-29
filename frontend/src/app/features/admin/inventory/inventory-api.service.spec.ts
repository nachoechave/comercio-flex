import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { InventoryApiService } from './inventory-api.service';

describe('InventoryApiService', () => {
  let service: InventoryApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(InventoryApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists inventory with tenant, pagination, q and availability', () => {
    service.list('tienda-a', 2, 20, 'REM-M', 'IN_STOCK').subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/stores/tienda-a/admin/inventory' &&
        candidate.params.get('page') === '2',
    );
    expect(request.request.params.get('q')).toBe('REM-M');
    expect(request.request.params.get('availability')).toBe('IN_STOCK');
    request.flush({ items: [], page: 2, size: 20, totalItems: 0, totalPages: 0 });
  });

  it('loads one variant and its paged movements', () => {
    service.get('tienda-a', 'variant-1').subscribe();
    http
      .expectOne('/api/v1/stores/tienda-a/admin/inventory/variants/variant-1')
      .flush({});

    service.movements('tienda-a', 'variant-1', 1, 20).subscribe();
    const movements = http.expectOne(
      (candidate) =>
        candidate.url.endsWith('/inventory/variants/variant-1/movements') &&
        candidate.params.get('page') === '1',
    );
    movements.flush({ items: [], page: 1, size: 20, totalItems: 0, totalPages: 0 });
  });

  it('sends adjustment body and stable idempotency header', () => {
    service
      .adjust('tienda-a', 'variant-1', '11111111-1111-4111-8111-111111111111', {
        direction: 'DECREASE',
        quantity: '2',
        reason: 'DAMAGE',
        note: 'Prenda dañada',
      })
      .subscribe();
    const request = http.expectOne(
      '/api/v1/stores/tienda-a/admin/inventory/variants/variant-1/adjustments',
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBe(
      '11111111-1111-4111-8111-111111111111',
    );
    expect(request.request.body).toEqual({
      direction: 'DECREASE',
      quantity: '2',
      reason: 'DAMAGE',
      note: 'Prenda dañada',
    });
    request.flush({});
  });
});
