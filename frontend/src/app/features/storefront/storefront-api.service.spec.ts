import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StorefrontApiService } from './storefront-api.service';

describe('StorefrontApiService', () => {
  let service: StorefrontApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [StorefrontApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(StorefrontApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads public settings and categories for the selected store', () => {
    service.getSettings('tienda-a').subscribe();
    const settings = http.expectOne('/api/v1/stores/tienda-a/settings');
    expect(settings.request.method).toBe('GET');
    settings.flush({});

    service.listCategories('tienda-a').subscribe();
    const categories = http.expectOne('/api/v1/stores/tienda-a/catalog/categories');
    expect(categories.request.method).toBe('GET');
    categories.flush([]);
  });

  it('encodes tenant and product slugs in public URLs', () => {
    service.getProduct('tienda a', 'remera/azul').subscribe();
    const request = http.expectOne('/api/v1/stores/tienda%20a/catalog/products/remera%2Fazul');
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('sends pagination and optional catalog filters', () => {
    service
      .listProducts('tienda-a', {
        page: 2,
        size: 24,
        q: 'remera azul',
        category: 'infantil',
      })
      .subscribe();

    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/stores/tienda-a/catalog/products' &&
        candidate.params.get('page') === '2' &&
        candidate.params.get('size') === '24',
    );
    expect(request.request.params.get('q')).toBe('remera azul');
    expect(request.request.params.get('category')).toBe('infantil');
    request.flush({ items: [], page: 2, size: 24, totalItems: 0, totalPages: 0 });
  });

  it('omits empty optional filters', () => {
    service.listProducts('tienda-a', { page: 0, size: 24 }).subscribe();
    const request = http.expectOne('/api/v1/stores/tienda-a/catalog/products?page=0&size=24');
    expect(request.request.params.has('q')).toBe(false);
    expect(request.request.params.has('category')).toBe(false);
    request.flush({ items: [], page: 0, size: 24, totalItems: 0, totalPages: 0 });
  });

  it('creates and retrieves a guest order with its private headers and token', () => {
    const body = {
      customerName: 'Ana Pérez',
      customerPhone: '11 5555 1234',
      items: [{ variantId: 'variant-1', quantity: '2' }],
    };
    service.createOrder('tienda-a', 'key-1', body).subscribe();
    const create = http.expectOne('/api/v1/stores/tienda-a/orders');
    expect(create.request.method).toBe('POST');
    expect(create.request.headers.get('Idempotency-Key')).toBe('key-1');
    expect(create.request.body).toEqual(body);
    expect(create.request.body.items[0].unitPrice).toBeUndefined();
    create.flush({});

    service.getOrder('tienda-a', 'order/1', 'private-token').subscribe();
    const find = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/stores/tienda-a/orders/order%2F1' &&
        candidate.params.get('token') === 'private-token',
    );
    expect(find.request.method).toBe('GET');
    find.flush({});
  });
});
