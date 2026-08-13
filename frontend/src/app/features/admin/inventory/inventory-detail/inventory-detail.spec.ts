import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { InventoryDetail } from './inventory-detail';

describe('InventoryDetail', () => {
  let fixture: ComponentFixture<InventoryDetail>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InventoryDetail],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ variantId: 'variant-1' }),
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
    fixture = TestBed.createComponent(InventoryDetail);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('shows formatted quantities and the immutable movement context', () => {
    http
      .expectOne('/api/v1/stores/tienda-a/admin/inventory/variants/variant-1')
      .flush({
        variantId: 'variant-1',
        productId: 'product-1',
        productName: 'Remera',
        productStatus: 'ARCHIVED',
        sku: 'REM-M',
        size: 'M',
        color: 'Negro',
        variantActive: false,
        quantity: '5.000',
        version: 2,
        updatedAt: '2026-07-29T12:00:00Z',
      });
    http
      .expectOne(
        (request) =>
          request.url.endsWith('/inventory/variants/variant-1/movements') &&
          request.params.get('page') === '0',
      )
      .flush({
        items: [
          {
            id: 'movement-1',
            direction: 'DECREASE',
            delta: '-3.000',
            quantityBefore: '8.000',
            quantityAfter: '5.000',
            reason: 'DAMAGE',
            note: 'Prenda dañada',
            actor: { id: 'user-1', displayName: 'Operador Demo' },
            createdAt: '2026-07-29T12:00:00Z',
          },
        ],
        page: 0,
        size: 20,
        totalItems: 1,
        totalPages: 1,
      });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('producto está archivado');
    expect(fixture.nativeElement.textContent).toContain('-3');
    expect(fixture.nativeElement.textContent).not.toContain('.000');
    expect(fixture.nativeElement.textContent).toContain('Daño o pérdida');
    expect(fixture.nativeElement.textContent).toContain('Operador Demo');
  });
});
