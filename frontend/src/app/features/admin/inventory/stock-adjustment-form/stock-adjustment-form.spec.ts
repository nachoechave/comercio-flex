import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { StockAdjustmentForm } from './stock-adjustment-form';

const INVENTORY = {
  variantId: 'variant-1',
  productId: 'product-1',
  productName: 'Remera',
  productStatus: 'DRAFT' as const,
  sku: 'REM-M',
  size: 'M',
  color: 'Negro',
  variantActive: true,
  quantity: '8.000',
  version: 2,
  updatedAt: '2026-07-29T12:00:00Z',
};

describe('StockAdjustmentForm', () => {
  let fixture: ComponentFixture<StockAdjustmentForm>;
  let http: HttpTestingController;

  beforeEach(async () => {
    const store = { paramMap: convertToParamMap({ storeSlug: 'tienda-a' }), parent: null };
    await TestBed.configureTestingModule({
      imports: [StockAdjustmentForm],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ variantId: 'variant-1' }),
              parent: store,
            },
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(StockAdjustmentForm);
    fixture.detectChanges();
    http
      .expectOne('/api/v1/stores/tienda-a/admin/inventory/variants/variant-1')
      .flush(INVENTORY);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    http.verify();
  });

  it('accepts only positive integer units and previews exact thousandths', () => {
    const quantity = fixture.componentInstance.form.controls.quantity;
    for (const valid of ['1', '999999999999']) {
      quantity.setValue(valid);
      expect(quantity.valid, valid).toBe(true);
    }
    for (const invalid of ['0', '-1', '1.0', '01', '1000000000000']) {
      quantity.setValue(invalid);
      expect(quantity.invalid, invalid).toBe(true);
    }

    fixture.componentInstance.form.patchValue({ direction: 'DECREASE', quantity: '3' });
    expect(fixture.componentInstance.preview()).toEqual({
      current: '8.000',
      operator: '−',
      quantity: '3.000',
      result: '5.000',
      valid: true,
    });
  });

  it('requires a note for OTHER and rejects a negative result locally', () => {
    fixture.componentInstance.form.setValue({
      direction: 'DECREASE',
      quantity: '9',
      reason: 'OTHER',
      note: '',
    });
    fixture.componentInstance.submit();
    expect(fixture.componentInstance.errorMessage()).toContain('Explicá el motivo');
    http.expectNone(
      '/api/v1/stores/tienda-a/admin/inventory/variants/variant-1/adjustments',
    );

    fixture.componentInstance.form.controls.note.setValue('Conteo');
    fixture.componentInstance.submit();
    expect(fixture.componentInstance.errorMessage()).toContain('negativa');
  });

  it('keeps the same idempotency key for a manual retry after a timeout', () => {
    const uuid = '11111111-1111-4111-8111-111111111111';
    vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue(uuid);
    fixture.componentInstance.form.setValue({
      direction: 'INCREASE',
      quantity: '2',
      reason: 'RECEIPT',
      note: '',
    });

    fixture.componentInstance.submit();
    const first = http.expectOne(
      '/api/v1/stores/tienda-a/admin/inventory/variants/variant-1/adjustments',
    );
    expect(first.request.headers.get('Idempotency-Key')).toBe(uuid);
    first.error(new ProgressEvent('network'));
    fixture.detectChanges();
    expect(fixture.componentInstance.uncertainResult()).toBe(true);

    fixture.componentInstance.submit();
    const retry = http.expectOne(
      '/api/v1/stores/tienda-a/admin/inventory/variants/variant-1/adjustments',
    );
    expect(retry.request.headers.get('Idempotency-Key')).toBe(uuid);
    retry.flush({
      inventory: { ...INVENTORY, quantity: '10.000', version: 3 },
      movement: {
        id: 'movement-1',
        direction: 'INCREASE',
        delta: '2.000',
        quantityBefore: '8.000',
        quantityAfter: '10.000',
        reason: 'RECEIPT',
        note: null,
        actor: { id: 'user-1', displayName: 'Operador' },
        createdAt: '2026-07-29T12:01:00Z',
      },
    });
    fixture.detectChanges();
    expect(fixture.componentInstance.successMessage()).toContain('10.');
    expect(fixture.componentInstance.successMessage()).not.toContain('10.000');
  });
});

describe('StockAdjustmentForm tenant reuse', () => {
  let fixture: ComponentFixture<StockAdjustmentForm>;
  let http: HttpTestingController;
  let params: BehaviorSubject<ParamMap>;

  beforeEach(async () => {
    params = new BehaviorSubject(
      convertToParamMap({ storeSlug: 'tienda-a', variantId: 'variant-1' }),
    );
    await TestBed.configureTestingModule({
      imports: [StockAdjustmentForm],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            pathFromRoot: [{ paramMap: params.asObservable() }],
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
    fixture = TestBed.createComponent(StockAdjustmentForm);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('cancels A and clears its form before loading B', () => {
    const storeA = http.expectOne(
      '/api/v1/stores/tienda-a/admin/inventory/variants/variant-1',
    );
    fixture.componentInstance.form.patchValue({ quantity: '5', note: 'Tenant A' });

    params.next(convertToParamMap({ storeSlug: 'tienda-b', variantId: 'variant-1' }));
    fixture.detectChanges();
    expect(storeA.cancelled).toBe(true);
    expect(fixture.componentInstance.form.controls.quantity.value).toBe('');
    expect(fixture.componentInstance.form.controls.note.value).toBe('');

    http
      .expectOne('/api/v1/stores/tienda-b/admin/inventory/variants/variant-1')
      .flush({ ...INVENTORY, productName: 'Producto B' });
    fixture.detectChanges();
    expect(fixture.componentInstance.inventory()?.productName).toBe('Producto B');
  });
});
