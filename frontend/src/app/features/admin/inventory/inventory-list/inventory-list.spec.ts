import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { AuthService } from '../../../../core/auth/auth.service';
import { InventoryList } from './inventory-list';

describe('InventoryList tenant reuse', () => {
  let fixture: ComponentFixture<InventoryList>;
  let http: HttpTestingController;
  let params: BehaviorSubject<ParamMap>;

  beforeEach(async () => {
    params = new BehaviorSubject(convertToParamMap({ storeSlug: 'tienda-a' }));
    await TestBed.configureTestingModule({
      imports: [InventoryList],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { membershipFor: () => ({ role: 'OWNER' }) } },
        {
          provide: ActivatedRoute,
          useValue: {
            pathFromRoot: [{ paramMap: params.asObservable() }],
            snapshot: {
              paramMap: convertToParamMap({}),
              parent: { paramMap: convertToParamMap({ storeSlug: 'tienda-a' }), parent: null },
            },
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InventoryList);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function flushThreshold(slug: string, threshold = '5.000'): void {
    http.expectOne(`/api/v1/stores/${slug}/admin/dashboard`).flush({ lowStockThreshold: threshold });
  }

  it('renders stock without insignificant decimal zeros', () => {
    flushThreshold('tienda-a');
    http
      .expectOne((request) => request.url.includes('/tienda-a/admin/inventory'))
      .flush({
        items: [
          {
            variantId: 'variant-1',
            productId: 'product-1',
            productName: 'Remera',
            productStatus: 'PUBLISHED',
            sku: 'REM-M',
            size: 'M',
            color: 'Negro',
            variantActive: true,
            quantity: '10.000',
            version: 1,
            updatedAt: '2026-08-13T12:00:00Z',
          },
        ],
        page: 0,
        size: 20,
        totalItems: 1,
        totalPages: 1,
      });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.quantity').textContent.trim()).toBe('10');
    expect(fixture.nativeElement.textContent).toContain('Normal');
  });

  it('does not infer a normal stock status when the tenant threshold is unavailable', () => {
    http
      .expectOne('/api/v1/stores/tienda-a/admin/dashboard')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    http
      .expectOne((request) => request.url.includes('/tienda-a/admin/inventory'))
      .flush({
        items: [
          {
            variantId: 'variant-1',
            productId: 'product-1',
            productName: 'Remera',
            productStatus: 'PUBLISHED',
            sku: 'REM-M',
            size: 'M',
            color: 'Negro',
            variantActive: true,
            quantity: '1.000',
            version: 1,
            updatedAt: '2026-08-13T12:00:00Z',
          },
        ],
        page: 0,
        size: 20,
        totalItems: 1,
        totalPages: 1,
      });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Estado no disponible');
    expect(fixture.nativeElement.textContent).not.toContain('Normal');
  });

  it('resets page and filters when navigating A to B', () => {
    flushThreshold('tienda-a');
    http
      .expectOne((request) => request.url.includes('/tienda-a/admin/inventory'))
      .flush({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 3 });
    fixture.detectChanges();
    fixture.componentInstance.filters.setValue({ q: 'REM', availability: 'OUT_OF_STOCK' });
    fixture.componentInstance.goToPage(2);
    fixture.detectChanges();
    const pageA = http.expectOne(
      (request) =>
        request.url.includes('/tienda-a/admin/inventory') &&
        request.params.get('page') === '2',
    );
    expect(pageA.request.params.get('q')).toBe('REM');
    pageA.flush({ items: [], page: 2, size: 20, totalItems: 0, totalPages: 3 });

    params.next(convertToParamMap({ storeSlug: 'tienda-b' }));
    fixture.detectChanges();
    flushThreshold('tienda-b');
    const pageB = http.expectOne(
      (request) =>
        request.url.includes('/tienda-b/admin/inventory') &&
        request.params.get('page') === '0',
    );
    expect(pageB.request.params.has('q')).toBe(false);
    expect(pageB.request.params.get('availability')).toBe('ALL');
    expect(fixture.componentInstance.page().items).toEqual([]);
    pageB.flush({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  });
});
