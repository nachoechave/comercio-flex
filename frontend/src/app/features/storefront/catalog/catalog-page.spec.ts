import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, provideRouter } from '@angular/router';
import { Meta, Title } from '@angular/platform-browser';
import { BehaviorSubject } from 'rxjs';

import { StorefrontApiService } from '../storefront-api.service';
import { StorefrontContextService } from '../storefront-context.service';
import { StoreSettings } from '../storefront.models';
import { CatalogPage } from './catalog-page';

describe('CatalogPage', () => {
  let fixture: ComponentFixture<CatalogPage>;
  let http: HttpTestingController;
  let storeParams: BehaviorSubject<ParamMap>;
  let queryParams: BehaviorSubject<ParamMap>;
  const settings = signal<StoreSettings | null>({
    slug: 'tienda-a',
    storeName: 'Tienda A',
    currencyCode: 'ARS',
    timezone: 'America/Argentina/Buenos_Aires',
  });
  const context = {
    settings,
    currencyCode: computed(() => settings()?.currencyCode ?? 'ARS'),
  };

  beforeEach(async () => {
    settings.set({
      slug: 'tienda-a',
      storeName: 'Tienda A',
      currencyCode: 'ARS',
      timezone: 'America/Argentina/Buenos_Aires',
    });
    storeParams = new BehaviorSubject(convertToParamMap({ storeSlug: 'tienda-a' }));
    queryParams = new BehaviorSubject(
      convertToParamMap({ q: 'remera', categoria: 'infantil', page: '2' }),
    );

    await TestBed.configureTestingModule({
      imports: [CatalogPage],
      providers: [
        StorefrontApiService,
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: StorefrontContextService, useValue: context },
        {
          provide: ActivatedRoute,
          useValue: {
            pathFromRoot: [{ paramMap: storeParams.asObservable() }],
            queryParamMap: queryParams.asObservable(),
            snapshot: {
              paramMap: convertToParamMap({}),
              queryParamMap: queryParams.value,
              parent: { paramMap: storeParams.value, parent: null },
            },
          },
        },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function create(): void {
    fixture = TestBed.createComponent(CatalogPage);
    fixture.detectChanges();
  }

  function flushCategories(store = 'tienda-a'): void {
    http.expectOne(`/api/v1/stores/${store}/catalog/categories`).flush([
      { id: 'category-1', name: 'Infantil', slug: 'infantil' },
    ]);
  }

  it('restores q, category and one-based page from the URL', () => {
    create();
    flushCategories();
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/stores/tienda-a/catalog/products' &&
        candidate.params.get('q') === 'remera' &&
        candidate.params.get('category') === 'infantil',
    );
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('24');
    request.flush({ items: [], page: 1, size: 24, totalItems: 0, totalPages: 2 });
    fixture.detectChanges();

    expect((fixture.componentInstance as any).filters.getRawValue()).toEqual({
      q: 'remera',
      category: 'infantil',
    });
    expect(fixture.nativeElement.textContent).toContain('No encontramos productos');
  });

  it('clears tenant-bound results before loading another tenant', () => {
    create();
    flushCategories();
    http.expectOne((request) => request.url.includes('/tienda-a/catalog/products')).flush({
      items: [
        {
          id: 'product-a',
          name: 'Remera A',
          slug: 'remera-a',
          category: { id: 'category-1', name: 'Infantil', slug: 'infantil' },
          priceFrom: '100.00',
          priceTo: '100.00',
          available: true,
        },
      ],
      page: 1,
      size: 24,
      totalItems: 1,
      totalPages: 2,
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Remera A');

    storeParams.next(convertToParamMap({ storeSlug: 'tienda-b' }));
    fixture.detectChanges();
    expect((fixture.componentInstance as any).page().items).toEqual([]);
    expect(fixture.nativeElement.textContent).toContain('Cargando productos');

    flushCategories('tienda-b');
    http.expectOne((request) => request.url.includes('/tienda-b/catalog/products')).flush({
      items: [],
      page: 1,
      size: 24,
      totalItems: 0,
      totalPages: 0,
    });
  });

  it('renders cards, ranges and availability without exposing quantities', () => {
    queryParams.next(convertToParamMap({}));
    create();
    flushCategories();
    http.expectOne((request) => request.url.includes('/catalog/products')).flush({
      items: [
        {
          id: 'product-1',
          name: 'Remera',
          slug: 'remera',
          category: { id: 'category-1', name: 'Infantil', slug: 'infantil' },
          priceFrom: '1000.00',
          priceTo: '1500.00',
          available: false,
          image: {
            id: 'image-1',
            url: '/media/image-1',
            thumbnailUrl: '/media/image-1/thumbnail',
            altText: 'Remera blanca doblada',
          },
        },
      ],
      page: 0,
      size: 24,
      totalItems: 1,
      totalPages: 1,
    });
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Desde $');
    expect(text).toContain('Sin stock');
    expect(text).not.toContain('cantidad');
    const image: HTMLImageElement = fixture.nativeElement.querySelector('.product-card img');
    expect(image.getAttribute('src')).toBe('/media/image-1/thumbnail');
    expect(image.alt).toBe('Remera blanca doblada');
  });

  it('shows a retryable error state', () => {
    create();
    flushCategories();
    http.expectOne((request) => request.url.includes('/catalog/products')).flush(
      { detail: 'No se pudo consultar el catálogo.' },
      { status: 503, statusText: 'Unavailable' },
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('No pudimos cargar el catálogo');
    expect(fixture.nativeElement.textContent).toContain('Reintentar');
  });

  it('resets catalog metadata while switching tenant and keeps it neutral on error', () => {
    queryParams.next(convertToParamMap({}));
    create();
    flushCategories();
    http.expectOne((request) => request.url.includes('/tienda-a/catalog/products')).flush({
      items: [],
      page: 0,
      size: 24,
      totalItems: 0,
      totalPages: 0,
    });
    fixture.detectChanges();
    expect(TestBed.inject(Title).getTitle()).toBe('Productos | Tienda A');

    settings.set(null);
    storeParams.next(convertToParamMap({ storeSlug: 'tienda-b' }));
    fixture.detectChanges();
    expect(TestBed.inject(Title).getTitle()).toBe('Catálogo | Comercio Flex');
    expect(TestBed.inject(Meta).getTag('name="description"')?.content).not.toContain('Tienda A');

    flushCategories('tienda-b');
    http.expectOne((request) => request.url.includes('/tienda-b/catalog/products')).flush(
      { detail: 'Tienda no encontrada.' },
      { status: 404, statusText: 'Not Found' },
    );
    fixture.detectChanges();
    expect(TestBed.inject(Title).getTitle()).toBe('Catálogo | Comercio Flex');
  });
});
