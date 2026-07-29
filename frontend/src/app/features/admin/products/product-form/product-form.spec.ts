import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { ProductForm } from './product-form';

describe('ProductForm creation', () => {
  let fixture: ComponentFixture<ProductForm>;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    const parent = { paramMap: convertToParamMap({ storeSlug: 'tienda-a' }), parent: null };
    await TestBed.configureTestingModule({
      imports: [ProductForm],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap({}), parent },
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(ProductForm);
    fixture.detectChanges();
    http.expectOne('/api/v1/stores/tienda-a/admin/categories').flush([
      { id: 'category-1', name: 'Remeras', active: true },
    ]);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('creates the product and its manual variant in one request', async () => {
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.componentInstance.form.setValue({
      name: '  Remera   clásica  ',
      description: '',
      categoryId: 'category-1',
    });
    const row = fixture.componentInstance.variants.at(0);
    row.patchValue({
      sku: 'REM-M-NEG',
      price: '19999.90',
      size: 'M',
      color: 'Negro',
    });

    fixture.componentInstance.submitProduct();
    const request = http.expectOne('/api/v1/stores/tienda-a/admin/products');
    expect(request.request.body).toEqual({
      name: 'Remera clásica',
      categoryId: 'category-1',
      variants: [
        { sku: 'REM-M-NEG', price: '19999.90', size: 'M', color: 'Negro' },
      ],
    });
    request.flush({ id: 'product-1' });
    await fixture.whenStable();
    expect(router.navigate).toHaveBeenCalled();
  });

  it('validates product name after trimming and normalizing spaces', () => {
    const name = fixture.componentInstance.form.controls.name;
    name.setValue('   ');
    expect(name.invalid).toBe(true);
    name.setValue('  a  ');
    expect(name.invalid).toBe(true);
    name.setValue('  Remera    clásica  ');
    expect(name.valid).toBe(true);
  });

  it('validates decimal text without converting it to a JavaScript number', () => {
    const price = fixture.componentInstance.variants.at(0).controls.price;
    for (const valid of ['0.01', '1', '1.5', '9999999999999.99']) {
      price.setValue(valid);
      expect(price.valid, valid).toBe(true);
    }
    for (const invalid of ['0', '0.00', '01.00', '1,50', '1.234', '10000000000000']) {
      price.setValue(invalid);
      expect(price.invalid, invalid).toBe(true);
    }
  });

  it('enforces SKU, size and color boundaries', () => {
    const row = fixture.componentInstance.variants.at(0);
    row.controls.sku.setValue('S'.repeat(64));
    row.controls.size.setValue('M'.repeat(60));
    row.controls.color.setValue('C'.repeat(60));
    expect(row.controls.sku.valid).toBe(true);
    expect(row.controls.size.valid).toBe(true);
    expect(row.controls.color.valid).toBe(true);
    row.controls.sku.setValue('S'.repeat(65));
    row.controls.size.setValue('M'.repeat(61));
    row.controls.color.setValue('C'.repeat(61));
    expect(row.controls.sku.invalid).toBe(true);
    expect(row.controls.size.invalid).toBe(true);
    expect(row.controls.color.invalid).toBe(true);
  });

  it('accepts normalized SKU characters and rejects spaces or symbols', () => {
    const sku = fixture.componentInstance.variants.at(0).controls.sku;
    for (const valid of ['a', 'Remera_M-01.2', '9-SKU']) {
      sku.setValue(valid);
      expect(sku.valid, valid).toBe(true);
    }
    for (const invalid of [' SKU', 'SKU CON ESPACIO', '_SKU', 'SKU@1']) {
      sku.setValue(invalid);
      expect(sku.invalid, invalid).toBe(true);
    }
  });

  it('preserves data and reports a 409 conflict', () => {
    fixture.componentInstance.form.setValue({
      name: 'Remera',
      description: '',
      categoryId: 'category-1',
    });
    fixture.componentInstance.variants.at(0).patchValue({ sku: 'DUP', price: '10.00' });
    fixture.componentInstance.submitProduct();
    http.expectOne('/api/v1/stores/tienda-a/admin/products').flush(
      { detail: 'El SKU ya está utilizado.' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('El SKU ya está utilizado.');
    expect(fixture.componentInstance.variants.at(0).controls.sku.value).toBe('DUP');
  });
});

describe('ProductForm tenant reuse', () => {
  let fixture: ComponentFixture<ProductForm>;
  let http: HttpTestingController;
  let params: BehaviorSubject<ParamMap>;

  beforeEach(async () => {
    params = new BehaviorSubject(
      convertToParamMap({ storeSlug: 'tienda-a', productId: 'product-1' }),
    );
    await TestBed.configureTestingModule({
      imports: [ProductForm],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            pathFromRoot: [{ paramMap: params.asObservable() }],
            snapshot: {
              paramMap: convertToParamMap({ productId: 'product-1' }),
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
    fixture = TestBed.createComponent(ProductForm);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('cancels A and reloads categories and product from B', () => {
    const categoryA = http.expectOne('/api/v1/stores/tienda-a/admin/categories');
    const productA = http.expectOne('/api/v1/stores/tienda-a/admin/products/product-1');

    params.next(convertToParamMap({ storeSlug: 'tienda-b', productId: 'product-1' }));
    fixture.detectChanges();
    expect(categoryA.cancelled).toBe(true);
    expect(productA.cancelled).toBe(true);

    http.expectOne('/api/v1/stores/tienda-b/admin/categories').flush([]);
    http.expectOne('/api/v1/stores/tienda-b/admin/products/product-1').flush({
      id: 'product-1',
      name: 'Producto B',
      slug: 'producto-b',
      description: null,
      status: 'DRAFT',
      category: { id: 'cat-b', name: 'Categoría B', active: true },
      variants: [],
      version: 1,
      createdAt: '',
      updatedAt: '',
    });
    fixture.detectChanges();
    expect(fixture.componentInstance.storeSlug()).toBe('tienda-b');
    expect(fixture.componentInstance.form.controls.name.value).toBe('Producto B');
  });
});
