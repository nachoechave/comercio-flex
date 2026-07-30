import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { OrderList } from './order-list';

describe('OrderList', () => {
  let fixture: ComponentFixture<OrderList>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrderList],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({}),
              parent: {
                paramMap: convertToParamMap({ storeSlug: 'tienda-a' }),
                parent: null,
              },
            },
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OrderList);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('applies number and status filters from the first page', () => {
    http
      .expectOne('/api/v1/stores/tienda-a/admin/orders?page=0&size=20')
      .flush({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
    fixture.componentInstance.filters.setValue({
      q: ' ORD-000021 ',
      status: 'CONFIRMED',
    });

    fixture.componentInstance.search();
    fixture.detectChanges();

    http
      .expectOne(
        '/api/v1/stores/tienda-a/admin/orders?page=0&size=20&q=ORD-000021&status=CONFIRMED',
      )
      .flush({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  });

  it('requests the next page when it exists', () => {
    http
      .expectOne('/api/v1/stores/tienda-a/admin/orders?page=0&size=20')
      .flush({ items: [], page: 0, size: 20, totalItems: 21, totalPages: 2 });

    fixture.componentInstance.goToPage(1);
    fixture.detectChanges();

    http
      .expectOne('/api/v1/stores/tienda-a/admin/orders?page=1&size=20')
      .flush({ items: [], page: 1, size: 20, totalItems: 21, totalPages: 2 });
  });
});
