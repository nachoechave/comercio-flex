import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

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

  it('resets page and filters when navigating A to B', () => {
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
