import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CategoryApiService } from './category-api.service';

describe('CategoryApiService', () => {
  let service: CategoryApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CategoryApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses the store slug when listing categories', () => {
    service.list('tienda-a').subscribe();
    const request = http.expectOne('/api/v1/stores/tienda-a/admin/categories');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('creates a category with its name', () => {
    service.create('tienda-a', { name: 'Remeras' }).subscribe();
    const request = http.expectOne('/api/v1/stores/tienda-a/admin/categories');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Remeras' });
    request.flush({});
  });

  it('loads and updates one category', () => {
    service.get('tienda-a', 'category-1').subscribe();
    http
      .expectOne('/api/v1/stores/tienda-a/admin/categories/category-1')
      .flush({});

    service.update('tienda-a', 'category-1', { name: 'Abrigos' }).subscribe();
    const update = http.expectOne(
      '/api/v1/stores/tienda-a/admin/categories/category-1',
    );
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({ name: 'Abrigos' });
    update.flush({});
  });

  it('changes status through the explicit status endpoint', () => {
    service.setActive('tienda-a', 'category-1', false).subscribe();
    const request = http.expectOne(
      '/api/v1/stores/tienda-a/admin/categories/category-1/status',
    );
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ active: false });
    request.flush({});
  });
});
