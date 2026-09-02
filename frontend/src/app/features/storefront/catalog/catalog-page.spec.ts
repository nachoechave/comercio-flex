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

const MODERN_BRANDING = {
  primaryColor: '#B7FF2A',
  secondaryColor: '#080808',
  backgroundColor: '#FFFFFF',
  textColor: '#0B0B0B',
  font: 'SANS' as const,
  heroTitle: 'Colección dinámica',
  heroSubtitle: 'Identidad configurada por plataforma.',
  template: 'FASHION' as const,
  logoUrl: null,
  faviconUrl: null,
  heroImageUrl: null,
};

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
    branding: MODERN_BRANDING,
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
      branding: MODERN_BRANDING,
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
    http
      .expectOne(`/api/v1/stores/${store}/catalog/categories`)
      .flush([{ id: 'category-1', name: 'Infantil', slug: 'infantil' }]);
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
    http
      .expectOne((request) => request.url.includes('/tienda-a/catalog/products'))
      .flush({
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
    http
      .expectOne((request) => request.url.includes('/tienda-b/catalog/products'))
      .flush({
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
    http
      .expectOne((request) => request.url.includes('/catalog/products'))
      .flush({
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

  it('renders the modern campaign from tenant branding instead of the store slug', () => {
    queryParams.next(convertToParamMap({}));
    create();
    flushCategories();
    http
      .expectOne((request) => request.url.includes('/catalog/products'))
      .flush({
        items: [
          {
            id: 'product-1',
            name: 'Remera urbana',
            slug: 'remera-urbana',
            category: { id: 'category-1', name: 'Infantil', slug: 'infantil' },
            priceFrom: '25000.00',
            priceTo: '25000.00',
            available: true,
            image: null,
          },
        ],
        page: 0,
        size: 24,
        totalItems: 1,
        totalPages: 1,
      });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.catalog--streetwear')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.streetwear-hero')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Colección dinámica');
    expect(fixture.nativeElement.textContent).toContain('Comprá por categoría');
    expect(fixture.nativeElement.textContent).toContain('Nuevos ingresos');
    expect(fixture.nativeElement.querySelector('.product-card--streetwear')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.category-card').getAttribute('href')).toContain(
      'categoria=infantil',
    );
    const sectionLinks = Array.from<HTMLAnchorElement>(
      fixture.nativeElement.querySelectorAll('.hero-primary, .hero-secondary, .campaign-grid a'),
    );
    expect(sectionLinks.map((link) => link.getAttribute('href'))).toEqual([
      '/tiendas/tienda-a#catalog-products',
      '/tiendas/tienda-a#category-section',
      '/tiendas/tienda-a#catalog-products',
      '/tiendas/tienda-a#catalog-products',
    ]);
  });

  it('keeps each category image mapped to its own stable category id', () => {
    queryParams.next(convertToParamMap({}));
    create();
    const categories = [
      {
        id: 'category-buzos',
        name: 'Buzos',
        slug: 'buzos',
        image: {
          id: 'image-buzos',
          url: '/media/buzos',
          thumbnailUrl: '/media/buzos/thumbnail',
          altText: 'Buzo negro',
        },
      },
      {
        id: 'category-camperas',
        name: 'Camperas',
        slug: 'camperas',
        image: {
          id: 'image-camperas',
          url: '/media/camperas',
          thumbnailUrl: '/media/camperas/thumbnail',
          altText: 'Campera azul',
        },
      },
      {
        id: 'category-remeras',
        name: 'Remeras',
        slug: 'remeras',
        image: {
          id: 'image-remeras',
          url: '/media/remeras',
          thumbnailUrl: '/media/remeras/thumbnail',
          altText: 'Remera blanca',
        },
      },
      { id: 'category-gorras', name: 'Gorras', slug: 'gorras', image: null },
    ];
    http.expectOne('/api/v1/stores/tienda-a/catalog/categories').flush(categories);
    http
      .expectOne((request) => request.url.includes('/catalog/products'))
      .flush({
        items: [],
        page: 0,
        size: 24,
        totalItems: 0,
        totalPages: 0,
      });
    fixture.detectChanges();

    const cards = Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('.category-card'));
    expect(cards).toHaveLength(4);
    expect(cards[0].querySelector('img')?.getAttribute('src')).toBe('/media/buzos/thumbnail');
    expect(cards[1].querySelector('img')?.getAttribute('src')).toBe('/media/camperas/thumbnail');
    expect(cards[2].querySelector('img')?.getAttribute('src')).toBe('/media/remeras/thumbnail');
    expect(cards[3].querySelector('img')).toBeNull();
    expect(cards[3].querySelector('.category-monogram')?.textContent?.trim()).toBe('G');

    queryParams.next(convertToParamMap({ categoria: 'camperas' }));
    fixture.detectChanges();
    http.expectOne('/api/v1/stores/tienda-a/catalog/categories').flush(categories);
    http
      .expectOne((request) => request.url.includes('/catalog/products'))
      .flush({
        items: [],
        page: 0,
        size: 24,
        totalItems: 0,
        totalPages: 0,
      });
    fixture.detectChanges();
    const refreshed = Array.from<HTMLElement>(
      fixture.nativeElement.querySelectorAll('.category-card'),
    );
    expect(refreshed[0].querySelector('img')?.getAttribute('src')).toBe('/media/buzos/thumbnail');
    expect(refreshed[1].querySelector('img')?.getAttribute('src')).toBe(
      '/media/camperas/thumbnail',
    );
  });

  it('keeps the standard catalog presentation for other stores', () => {
    settings.set({
      slug: 'tienda-b',
      storeName: 'Tienda B',
      currencyCode: 'ARS',
      timezone: 'America/Argentina/Buenos_Aires',
    });
    storeParams.next(convertToParamMap({ storeSlug: 'tienda-b' }));
    queryParams.next(convertToParamMap({}));
    create();
    flushCategories('tienda-b');
    http
      .expectOne((request) => request.url.includes('/tienda-b/catalog/products'))
      .flush({
        items: [],
        page: 0,
        size: 24,
        totalItems: 0,
        totalPages: 0,
      });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.catalog--streetwear')).toBeNull();
    expect(fixture.nativeElement.querySelector('.streetwear-hero')).toBeNull();
    expect(fixture.nativeElement.querySelector('.hero')).not.toBeNull();
  });

  it('renders a distinct minimal composition from the same catalog components', () => {
    settings.set({
      slug: 'tienda-a',
      storeName: 'Tienda A',
      currencyCode: 'ARS',
      timezone: 'America/Argentina/Buenos_Aires',
      branding: {
        ...MODERN_BRANDING,
        heroTitle: 'Objetos esenciales',
        heroSubtitle: 'Una selección simple y cuidada.',
        template: 'CATALOG',
      },
    });
    queryParams.next(convertToParamMap({}));
    create();
    flushCategories();
    http
      .expectOne((request) => request.url.includes('/catalog/products'))
      .flush({
        items: [
          {
            id: 'product-1',
            name: 'Producto esencial',
            slug: 'producto-esencial',
            category: { id: 'category-1', name: 'Infantil', slug: 'infantil' },
            priceFrom: '1000.00',
            priceTo: '1000.00',
            available: true,
            image: null,
          },
        ],
        page: 0,
        size: 24,
        totalItems: 1,
        totalPages: 1,
      });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.catalog--minimal')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.catalog--streetwear')).toBeNull();
    expect(fixture.nativeElement.querySelector('.product-card--minimal')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Objetos esenciales');
    expect(fixture.nativeElement.textContent).toContain('Una selección simple y cuidada.');
  });

  it('shows a retryable error state', () => {
    create();
    flushCategories();
    http
      .expectOne((request) => request.url.includes('/catalog/products'))
      .flush(
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
    http
      .expectOne((request) => request.url.includes('/tienda-a/catalog/products'))
      .flush({
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
    http
      .expectOne((request) => request.url.includes('/tienda-b/catalog/products'))
      .flush({ detail: 'Tienda no encontrada.' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(TestBed.inject(Title).getTitle()).toBe('Catálogo | Comercio Flex');
  });
});
