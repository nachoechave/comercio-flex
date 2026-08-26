import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  ActivatedRoute,
  convertToParamMap,
  ParamMap,
  provideRouter,
  Router,
} from '@angular/router';
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
    http
      .expectOne('/api/v1/stores/tienda-a/admin/categories')
      .flush([{ id: 'category-1', name: 'Remeras', active: true }]);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function fillValidProduct(): void {
    fixture.componentInstance.form.setValue({
      name: 'Remera clásica',
      description: '',
      categoryId: 'category-1',
    });
    fixture.componentInstance.variants.at(0).patchValue({
      sku: 'REM-M-NEG',
      price: '19999.90',
      size: 'M',
      color: 'Negro',
    });
  }

  function productResponse(overrides: Record<string, unknown> = {}) {
    return {
      id: 'product-1',
      name: 'Remera clásica',
      slug: 'remera-clasica',
      description: null,
      status: 'DRAFT',
      category: { id: 'category-1', name: 'Remeras', active: true },
      variants: [
        {
          id: 'variant-1',
          sku: 'REM-M-NEG',
          price: '19999.90',
          size: 'M',
          color: 'Negro',
          active: true,
          version: 0,
          createdAt: '',
          updatedAt: '',
        },
      ],
      image: null,
      version: 0,
      createdAt: '',
      updatedAt: '',
      ...overrides,
    };
  }

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
      variants: [{ sku: 'REM-M-NEG', price: '19999.90', size: 'M', color: 'Negro' }],
    });
    request.flush({ id: 'product-1' });
    await fixture.whenStable();
    expect(router.navigate).toHaveBeenCalled();
    http.expectNone((candidate) => candidate.url.endsWith('/status'));
  });

  it('creates multiple variants in the same draft request', () => {
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fillValidProduct();
    fixture.componentInstance.addVariant();
    fixture.componentInstance.variants.at(1).patchValue({
      sku: 'REM-L-BLA',
      price: '21999.90',
      size: 'L',
      color: 'Blanco',
    });

    fixture.componentInstance.submitProduct('DRAFT');

    const request = http.expectOne('/api/v1/stores/tienda-a/admin/products');
    expect(request.request.body.variants).toHaveLength(2);
    request.flush(productResponse());
  });

  it('previews and uploads the selected image after creating the draft', () => {
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const createObjectUrl = vi.fn(() => 'blob:new-product');
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: createObjectUrl });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() });
    fillValidProduct();
    const file = new File(['image'], 'remera.png', { type: 'image/png' });
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', { value: { item: () => file } });
    fixture.componentInstance.selectImage({ target: input } as unknown as Event);
    fixture.componentInstance.imageAltText.setValue('Remera negra');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.image-preview img')?.getAttribute('src')).toBe(
      'blob:new-product',
    );

    fixture.componentInstance.submitProduct('DRAFT');
    http.expectOne('/api/v1/stores/tienda-a/admin/products').flush(productResponse());
    http.expectOne('/api/v1/auth/csrf').flush({});
    const upload = http.expectOne('/api/v1/stores/tienda-a/admin/products/product-1/image');
    expect((upload.request.body as FormData).get('file')).toBe(file);
    upload.flush({
      id: 'image-1',
      url: '/media/image-1',
      thumbnailUrl: '/media/image-1/thumbnail',
      altText: 'Remera negra',
    });
    expect(router.navigate).toHaveBeenCalled();
  });

  it('registers initial stock through an auditable inventory adjustment', () => {
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fillValidProduct();
    fixture.componentInstance.variants.at(0).controls.initialStock.setValue('10');

    fixture.componentInstance.submitProduct('DRAFT');
    http.expectOne('/api/v1/stores/tienda-a/admin/products').flush(productResponse());
    const adjustment = http.expectOne(
      '/api/v1/stores/tienda-a/admin/inventory/variants/variant-1/adjustments',
    );
    expect(adjustment.request.headers.get('Idempotency-Key')).toBeTruthy();
    expect(adjustment.request.body).toEqual({
      direction: 'INCREASE',
      quantity: '10',
      reason: 'RECEIPT',
      note: 'Stock inicial registrado durante la creación del producto.',
    });
    adjustment.flush({ inventory: { variantId: 'variant-1', quantity: '10.000' } });
    expect(router.navigate).toHaveBeenCalled();
  });

  it('publishes only after product setup is complete and requires image warning confirmation', () => {
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fillValidProduct();

    fixture.componentInstance.submitProduct('PUBLISHED');
    expect(fixture.componentInstance.publishWithoutImageWarning()).toBe(true);
    http.expectNone('/api/v1/stores/tienda-a/admin/products');

    fixture.componentInstance.submitProduct('PUBLISHED');
    http.expectOne('/api/v1/stores/tienda-a/admin/products').flush(productResponse());
    const publication = http.expectOne('/api/v1/stores/tienda-a/admin/products/product-1/status');
    expect(publication.request.body).toEqual({ status: 'PUBLISHED', version: 0 });
    publication.flush(productResponse({ status: 'PUBLISHED', version: 1 }));
    expect(router.navigate).toHaveBeenCalled();
  });

  it('blocks a double submit while the create request is in flight', () => {
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fillValidProduct();

    fixture.componentInstance.submitProduct('DRAFT');
    fixture.componentInstance.submitProduct('DRAFT');

    const requests = http.match('/api/v1/stores/tienda-a/admin/products');
    expect(requests).toHaveLength(1);
    requests[0].flush(productResponse());
  });

  it('reports an upload failure and resumes on the created draft instead of duplicating it', () => {
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fillValidProduct();
    fixture.componentInstance.selectedImageFile.set(
      new File(['image'], 'remera.png', { type: 'image/png' }),
    );
    fixture.componentInstance.imageAltText.setValue('Remera negra');

    fixture.componentInstance.submitProduct('DRAFT');
    http.expectOne('/api/v1/stores/tienda-a/admin/products').flush(productResponse());
    http.expectOne('/api/v1/auth/csrf').flush({});
    http
      .expectOne('/api/v1/stores/tienda-a/admin/products/product-1/image')
      .flush({ detail: 'No se pudo almacenar la imagen.' }, { status: 503, statusText: 'Error' });

    expect(fixture.componentInstance.formError()).toContain('No se pudo almacenar la imagen.');
    expect(router.navigate).toHaveBeenCalledWith(
      ['/tiendas', 'tienda-a', 'admin', 'productos', 'product-1', 'editar'],
      { queryParams: { setup: 'image' } },
    );
  });

  it('creates a variant with arbitrary named options', () => {
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.componentInstance.form.setValue({
      name: 'Bolsa configurable',
      description: '',
      categoryId: 'category-1',
    });
    const row = fixture.componentInstance.variants.at(0);
    row.patchValue({ sku: 'BOLSA-ALG-M', price: '2500.00' });
    fixture.componentInstance.addVariantOption(0);
    fixture.componentInstance.addVariantOption(0);
    row.controls.options.at(0).setValue({ name: 'Material', value: 'Algodón' });
    row.controls.options.at(1).setValue({ name: 'Talle', value: 'M' });

    fixture.componentInstance.submitProduct();

    const request = http.expectOne('/api/v1/stores/tienda-a/admin/products');
    expect(request.request.body.variants[0]).toEqual({
      sku: 'BOLSA-ALG-M',
      price: '2500.00',
      options: [
        { name: 'Material', value: 'Algodón' },
        { name: 'Talle', value: 'M' },
      ],
    });
    request.flush({ id: 'product-1' });
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
    http
      .expectOne('/api/v1/stores/tienda-a/admin/products')
      .flush({ detail: 'El SKU ya está utilizado.' }, { status: 409, statusText: 'Conflict' });
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

describe('ProductForm image management', () => {
  let fixture: ComponentFixture<ProductForm>;
  let http: HttpTestingController;

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
            snapshot: { paramMap: convertToParamMap({ productId: 'product-1' }), parent },
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProductForm);
    fixture.detectChanges();
    http
      .expectOne('/api/v1/stores/tienda-a/admin/categories')
      .flush([{ id: 'category-1', name: 'Remeras', active: true }]);
    http.expectOne('/api/v1/stores/tienda-a/admin/products/product-1').flush({
      id: 'product-1',
      name: 'Remera',
      slug: 'remera',
      description: null,
      status: 'DRAFT',
      category: { id: 'category-1', name: 'Remeras', active: true },
      variants: [],
      image: {
        id: 'image-current',
        url: '/media/image-current',
        thumbnailUrl: '/media/image-current/thumbnail',
        altText: 'Imagen actual de la remera',
      },
      version: 1,
      createdAt: '',
      updatedAt: '',
    });
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('shows the current main image prominently while editing', () => {
    const image: HTMLImageElement = fixture.nativeElement.querySelector('.image-preview img');
    expect(image.getAttribute('src')).toBe('/media/image-current');
    expect(image.alt).toBe('Imagen actual de la remera');
  });

  it('rejects unsupported files before sending a request', () => {
    const file = new File(['plain text'], 'producto.txt', { type: 'text/plain' });
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', { value: { item: () => file } });

    fixture.componentInstance.selectImage({ target: input } as unknown as Event);

    expect(fixture.componentInstance.selectedImageFile()).toBeNull();
    expect(fixture.componentInstance.imageError()).toContain('JPEG o PNG');
  });

  it('uploads a valid image with trimmed alternative text', () => {
    const file = new File(['image'], 'producto.png', { type: 'image/png' });
    fixture.componentInstance.selectedImageFile.set(file);
    fixture.componentInstance.imageAltText.setValue('  Remera azul  ');

    fixture.componentInstance.uploadImage();
    http.expectOne('/api/v1/auth/csrf').flush({});
    const upload = http.expectOne('/api/v1/stores/tienda-a/admin/products/product-1/image');
    expect((upload.request.body as FormData).get('file')).toBe(file);
    expect((upload.request.body as FormData).get('altText')).toBe('Remera azul');
    upload.flush({
      id: 'image-1',
      url: '/media/image-1',
      thumbnailUrl: '/media/image-1/thumbnail',
      altText: 'Remera azul',
    });
    fixture.detectChanges();

    expect(fixture.componentInstance.product()?.image?.id).toBe('image-1');
    expect(fixture.nativeElement.textContent).toContain('imagen del producto fue guardada');
  });

  it('accepts exactly 5 MiB, rejects larger files and revokes the preview on destroy', () => {
    const createObjectUrl = vi.fn(() => 'blob:preview');
    const revokeObjectUrl = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: createObjectUrl });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: revokeObjectUrl });
    const oversized = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'large.png', {
      type: 'image/png',
    });
    const oversizedInput = document.createElement('input');
    Object.defineProperty(oversizedInput, 'files', { value: { item: () => oversized } });
    fixture.componentInstance.selectImage({ target: oversizedInput } as unknown as Event);
    expect(fixture.componentInstance.selectedImageFile()).toBeNull();
    expect(fixture.componentInstance.imageError()).toContain('5 MiB');

    const exactFile = new File([new Uint8Array(5 * 1024 * 1024)], 'exact.png', {
      type: 'image/png',
    });
    const exactInput = document.createElement('input');
    Object.defineProperty(exactInput, 'files', { value: { item: () => exactFile } });
    fixture.componentInstance.selectImage({ target: exactInput } as unknown as Event);
    expect(fixture.componentInstance.selectedImageFile()).toBe(exactFile);
    expect(fixture.componentInstance.imagePreviewUrl()).toBe('blob:preview');

    fixture.destroy();
    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:preview');
  });

  it('deletes an image with CSRF protection and exposes an accessible confirmation', () => {
    fixture.componentInstance.product.update((product) =>
      product
        ? {
            ...product,
            image: {
              id: 'image-1',
              url: '/media/image-1',
              thumbnailUrl: '/media/image-1/thumbnail',
              altText: 'Remera azul',
            },
          }
        : product,
    );
    fixture.detectChanges();
    const trigger: HTMLButtonElement = fixture.nativeElement.querySelector(
      '.image-actions button:not(.primary)',
    );
    fixture.componentInstance.requestImageRemoval({ currentTarget: trigger } as unknown as Event);
    fixture.detectChanges();
    const dialog: HTMLElement = fixture.nativeElement.querySelector('[role="alertdialog"]');
    expect(dialog.getAttribute('aria-modal')).toBe('true');
    expect(dialog.getAttribute('aria-describedby')).toBe('remove-image-description');

    fixture.componentInstance.deleteImage();
    http.expectOne('/api/v1/auth/csrf').flush({});
    const removal = http.expectOne('/api/v1/stores/tienda-a/admin/products/product-1/image');
    expect(removal.request.method).toBe('DELETE');
    removal.flush(null);
    fixture.detectChanges();

    expect(fixture.componentInstance.product()?.image).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('imagen del producto fue eliminada');
  });

  it('keeps the selected file available for retry after an upload error', () => {
    const file = new File(['image'], 'producto.png', { type: 'image/png' });
    fixture.componentInstance.selectedImageFile.set(file);
    fixture.componentInstance.imageAltText.setValue('Remera azul');
    fixture.componentInstance.successMessage.set('Mensaje anterior');

    fixture.componentInstance.uploadImage();
    expect(fixture.componentInstance.successMessage()).toBeNull();
    http.expectOne('/api/v1/auth/csrf').flush({});
    http
      .expectOne('/api/v1/stores/tienda-a/admin/products/product-1/image')
      .flush(
        { detail: 'No se pudo almacenar la imagen.' },
        { status: 503, statusText: 'Unavailable' },
      );
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedImageFile()).toBe(file);
    expect(fixture.componentInstance.imageError()).toContain('No se pudo almacenar');
  });

  it('cancels an in-flight upload when the component is destroyed', () => {
    const file = new File(['image'], 'producto.png', { type: 'image/png' });
    fixture.componentInstance.selectedImageFile.set(file);
    fixture.componentInstance.imageAltText.setValue('Remera azul');
    fixture.componentInstance.uploadImage();
    http.expectOne('/api/v1/auth/csrf').flush({});
    const upload = http.expectOne('/api/v1/stores/tienda-a/admin/products/product-1/image');

    fixture.destroy();

    expect(upload.cancelled).toBe(true);
  });
});

describe('ProductForm inventory editing', () => {
  let fixture: ComponentFixture<ProductForm>;
  let http: HttpTestingController;

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
            snapshot: { paramMap: convertToParamMap({ productId: 'product-1' }), parent },
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProductForm);
    fixture.detectChanges();
    http
      .expectOne('/api/v1/stores/tienda-a/admin/categories')
      .flush([{ id: 'category-1', name: 'Remeras', active: true }]);
    http.expectOne('/api/v1/stores/tienda-a/admin/products/product-1').flush({
      id: 'product-1',
      name: 'Remera',
      slug: 'remera',
      description: null,
      status: 'DRAFT',
      category: { id: 'category-1', name: 'Remeras', active: true },
      variants: [
        {
          id: 'variant-1',
          sku: 'REM-M',
          price: '1000.00',
          size: 'M',
          color: null,
          active: true,
          version: 1,
          createdAt: '',
          updatedAt: '',
        },
      ],
      image: null,
      version: 1,
      createdAt: '',
      updatedAt: '',
    });
    http.expectOne('/api/v1/stores/tienda-a/admin/inventory/variants/variant-1').flush({
      variantId: 'variant-1',
      productId: 'product-1',
      productName: 'Remera',
      productStatus: 'DRAFT',
      sku: 'REM-M',
      size: 'M',
      color: null,
      variantActive: true,
      quantity: '10.000',
      version: 1,
      updatedAt: '',
    });
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('shows current stock and routes adjustments through the audited inventory flow', () => {
    expect(fixture.nativeElement.textContent).toContain('Stock actual:');
    expect(fixture.nativeElement.textContent).toContain('10.000');
    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('.inventory-row a');
    expect(link.getAttribute('href')).toBe('/tiendas/tienda-a/admin/inventario/variant-1/ajustar');
  });

  it('updates a variant price without replacing or dropping the variant', () => {
    const row = fixture.componentInstance.variants.at(0);
    row.controls.price.setValue('1250.00');

    fixture.componentInstance.saveVariant(0);

    const update = http.expectOne(
      '/api/v1/stores/tienda-a/admin/products/product-1/variants/variant-1',
    );
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({
      sku: 'REM-M',
      price: '1250.00',
      options: [{ name: 'Talle', value: 'M' }],
      version: 1,
    });
    update.flush({
      id: 'variant-1',
      sku: 'REM-M',
      price: '1250.00',
      size: 'M',
      color: null,
      options: [{ name: 'Talle', value: 'M' }],
      active: true,
      version: 2,
      createdAt: '',
      updatedAt: '',
    });
    http.expectOne('/api/v1/stores/tienda-a/admin/inventory/variants/variant-1').flush({
      variantId: 'variant-1',
      productId: 'product-1',
      productName: 'Remera',
      productStatus: 'DRAFT',
      sku: 'REM-M',
      size: 'M',
      color: null,
      variantActive: true,
      quantity: '10.000',
      version: 1,
      updatedAt: '',
    });
    expect(fixture.componentInstance.variants).toHaveLength(1);
    expect(fixture.componentInstance.variants.at(0).controls.price.value).toBe('1250.00');
  });
});
