import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ProductApiService } from './product-api.service';

describe('ProductApiService', () => {
  let service: ProductApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProductApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends pagination and optional filters when listing', () => {
    service
      .list('tienda-a', {
        page: 2,
        size: 20,
        query: 'remera',
        status: 'DRAFT',
        categoryId: 'category-1',
      })
      .subscribe();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/stores/tienda-a/admin/products' &&
        candidate.params.get('page') === '2' &&
        candidate.params.get('q') === 'remera',
    );
    expect(request.request.params.get('status')).toBe('DRAFT');
    expect(request.request.params.get('categoryId')).toBe('category-1');
    request.flush({ items: [], page: 2, size: 20, totalItems: 0, totalPages: 0 });
  });

  it('creates a product and variants atomically with decimal strings', () => {
    const body = {
      name: 'Remera',
      categoryId: 'category-1',
      variants: [{ sku: 'REM-M-NEG', price: '19999.90', size: 'M', color: 'Negro' }],
    };
    service.create('tienda-a', body).subscribe();
    const request = http.expectOne('/api/v1/stores/tienda-a/admin/products');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush({});
  });

  it('updates product status with its optimistic version', () => {
    service.setStatus('tienda-a', 'product-1', 'PUBLISHED', 4).subscribe();
    const request = http.expectOne(
      '/api/v1/stores/tienda-a/admin/products/product-1/status',
    );
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ status: 'PUBLISHED', version: 4 });
    request.flush({});
  });

  it('uploads and deletes the main image using the product image endpoint', () => {
    const file = new File(['image'], 'producto.png', { type: 'image/png' });
    service.uploadImage('tienda-a', 'product-1', file, 'Remera azul').subscribe();
    const upload = http.expectOne('/api/v1/stores/tienda-a/admin/products/product-1/image');
    expect(upload.request.method).toBe('PUT');
    expect(upload.request.body).toBeInstanceOf(FormData);
    expect((upload.request.body as FormData).get('file')).toBe(file);
    expect((upload.request.body as FormData).get('altText')).toBe('Remera azul');
    upload.flush({
      id: 'image-1',
      url: '/media/image-1',
      thumbnailUrl: '/media/image-1/thumbnail',
      altText: 'Remera azul',
    });

    service.deleteImage('tienda-a', 'product-1').subscribe();
    const removal = http.expectOne('/api/v1/stores/tienda-a/admin/products/product-1/image');
    expect(removal.request.method).toBe('DELETE');
    removal.flush(null);
  });

  it('uses independent versioned endpoints for variant changes', () => {
    service
      .updateVariant('tienda-a', 'product-1', 'variant-1', {
        sku: 'REM-M',
        price: '10.50',
        size: 'M',
        version: 3,
      })
      .subscribe();
    const update = http.expectOne(
      '/api/v1/stores/tienda-a/admin/products/product-1/variants/variant-1',
    );
    expect(update.request.body.version).toBe(3);
    update.flush({});

    service
      .setVariantActive('tienda-a', 'product-1', 'variant-1', false, 4)
      .subscribe();
    const status = http.expectOne(
      '/api/v1/stores/tienda-a/admin/products/product-1/variants/variant-1/status',
    );
    expect(status.request.body).toEqual({ active: false, version: 4 });
    status.flush({});
  });
});
