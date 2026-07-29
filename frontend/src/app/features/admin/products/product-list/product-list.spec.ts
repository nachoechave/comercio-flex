import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { AuthService } from '../../../../core/auth/auth.service';
import { ProductList } from './product-list';

describe('ProductList', () => {
  let fixture: ComponentFixture<ProductList>;
  let http: HttpTestingController;
  let params: BehaviorSubject<ParamMap>;
  let role: 'OWNER' | 'STAFF';

  beforeEach(async () => {
    role = 'OWNER';
    params = new BehaviorSubject(convertToParamMap({ storeSlug: 'tienda-a' }));
    await TestBed.configureTestingModule({
      imports: [ProductList],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            pathFromRoot: [{ paramMap: params.asObservable() }],
            snapshot: {
              paramMap: convertToParamMap({}),
              parent: {
                paramMap: convertToParamMap({ storeSlug: 'tienda-a' }),
                parent: null,
              },
            },
          },
        },
        {
          provide: AuthService,
          useValue: {
            membershipFor: () => ({ role }),
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function createAndFlush(): void {
    fixture = TestBed.createComponent(ProductList);
    fixture.detectChanges();
    http
      .expectOne((request) => request.url.includes('/tienda-a/admin/products'))
      .flush({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 3 });
    http.expectOne('/api/v1/stores/tienda-a/admin/categories').flush([]);
    fixture.detectChanges();
  }

  it('shows staff the read-only list without a create action', () => {
    role = 'STAFF';
    createAndFlush();
    expect(fixture.nativeElement.textContent).toContain('Tenés acceso de lectura');
    expect(fixture.nativeElement.querySelector('a.primary')).toBeNull();
  });

  it('resets page and tenant-bound filters when reused from A to B', () => {
    createAndFlush();
    fixture.componentInstance.filters.setValue({
      query: 'remera',
      status: 'DRAFT',
      categoryId: 'category-a',
    });
    fixture.componentInstance.goToPage(2);
    fixture.detectChanges();

    const pageA = http.expectOne(
      (request) =>
        request.url.includes('/tienda-a/admin/products') &&
        request.params.get('page') === '2',
    );
    expect(pageA.request.params.get('categoryId')).toBe('category-a');
    pageA.flush({ items: [], page: 2, size: 20, totalItems: 0, totalPages: 3 });
    http.expectOne('/api/v1/stores/tienda-a/admin/categories').flush([]);

    params.next(convertToParamMap({ storeSlug: 'tienda-b' }));
    fixture.detectChanges();
    const pageB = http.expectOne(
      (request) =>
        request.url.includes('/tienda-b/admin/products') &&
        request.params.get('page') === '0',
    );
    expect(pageB.request.params.has('q')).toBe(false);
    expect(pageB.request.params.has('status')).toBe(false);
    expect(pageB.request.params.has('categoryId')).toBe(false);
    expect(fixture.componentInstance.page().items).toEqual([]);
    pageB.flush({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
    http.expectOne('/api/v1/stores/tienda-b/admin/categories').flush([]);
  });
});
