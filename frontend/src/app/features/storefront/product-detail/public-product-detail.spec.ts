import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Meta, Title } from '@angular/platform-browser';
import { BehaviorSubject } from 'rxjs';

import { StorefrontApiService } from '../storefront-api.service';
import { StorefrontContextService } from '../storefront-context.service';
import { StoreSettings } from '../storefront.models';
import { PublicProductDetail } from './public-product-detail';

describe('PublicProductDetail', () => {
  let fixture: ComponentFixture<PublicProductDetail>;
  let http: HttpTestingController;
  const params = new BehaviorSubject(
    convertToParamMap({ storeSlug: 'tienda-a', productSlug: 'remera-azul' }),
  );
  const settings = signal<StoreSettings | null>({
    slug: 'tienda-a',
    storeName: 'Tienda A',
    currencyCode: 'ARS',
    timezone: 'America/Argentina/Buenos_Aires',
  });

  beforeEach(async () => {
    params.next(convertToParamMap({ storeSlug: 'tienda-a', productSlug: 'remera-azul' }));
    settings.set({
      slug: 'tienda-a',
      storeName: 'Tienda A',
      currencyCode: 'ARS',
      timezone: 'America/Argentina/Buenos_Aires',
    });
    await TestBed.configureTestingModule({
      imports: [PublicProductDetail],
      providers: [
        StorefrontApiService,
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: StorefrontContextService,
          useValue: {
            settings,
            currencyCode: computed(() => settings()?.currencyCode ?? 'ARS'),
          },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            pathFromRoot: [{ paramMap: params.asObservable() }],
            snapshot: {
              paramMap: convertToParamMap({}),
              parent: { paramMap: params.value, parent: null },
            },
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function create(): void {
    fixture = TestBed.createComponent(PublicProductDetail);
    fixture.detectChanges();
  }

  it('renders public variants and updates the document title', () => {
    create();
    http.expectOne(
      '/api/v1/stores/tienda-a/catalog/products/remera-azul',
    ).flush({
      id: 'product-1',
      name: 'Remera azul',
      slug: 'remera-azul',
      description: 'Algodón suave.',
      category: { id: 'category-1', name: 'Remeras', slug: 'remeras' },
      variants: [
        { id: 'variant-1', price: '2500.00', size: 'M', color: 'Azul', available: true },
        { id: 'variant-2', price: '2600.00', size: 'L', color: 'Azul', available: false },
      ],
    });
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Remera azul');
    expect(text).toContain('Talle M · Azul');
    expect(text).toContain('Talle L · Azul');
    expect(text).toContain('Sin stock');
    expect(TestBed.inject(Title).getTitle()).toBe('Remera azul | Tienda A');
  });

  it('renders a product-specific not-found state', () => {
    create();
    http.expectOne(
      '/api/v1/stores/tienda-a/catalog/products/remera-azul',
    ).flush(
      { detail: 'Producto no encontrado.' },
      { status: 404, statusText: 'Not Found' },
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No encontramos este producto');
    expect(fixture.nativeElement.textContent).toContain('Volver al catálogo');
    expect(fixture.nativeElement.textContent).not.toContain('Reintentar');
  });

  it('keeps neutral metadata until settings arrive and then updates reactively', () => {
    settings.set(null);
    create();
    http.expectOne(
      '/api/v1/stores/tienda-a/catalog/products/remera-azul',
    ).flush({
      id: 'product-1',
      name: 'Remera azul',
      slug: 'remera-azul',
      description: 'Algodón suave.',
      category: { id: 'category-1', name: 'Remeras', slug: 'remeras' },
      variants: [{ id: 'variant-1', price: '2500.00', size: 'M', color: 'Azul', available: true }],
    });
    fixture.detectChanges();

    expect(TestBed.inject(Title).getTitle()).toBe('Producto | Comercio Flex');
    expect(TestBed.inject(Meta).getTag('name="description"')?.content).not.toContain(
      'Algodón suave',
    );

    settings.set({
      slug: 'tienda-a',
      storeName: 'Tienda A',
      currencyCode: 'ARS',
      timezone: 'America/Argentina/Buenos_Aires',
    });
    fixture.detectChanges();
    expect(TestBed.inject(Title).getTitle()).toBe('Remera azul | Tienda A');
    expect(TestBed.inject(Meta).getTag('name="description"')?.content).toBe('Algodón suave.');
  });

  it('cleans product and metadata when switching tenant and keeps them neutral on 404', () => {
    create();
    http.expectOne(
      '/api/v1/stores/tienda-a/catalog/products/remera-azul',
    ).flush({
      id: 'product-a',
      name: 'Producto A',
      slug: 'remera-azul',
      description: 'Descripción privada de A.',
      category: { id: 'category-1', name: 'Remeras', slug: 'remeras' },
      variants: [{ id: 'variant-a', price: '100.00', size: null, color: null, available: true }],
    });
    fixture.detectChanges();
    expect(TestBed.inject(Title).getTitle()).toBe('Producto A | Tienda A');

    settings.set(null);
    params.next(convertToParamMap({ storeSlug: 'tienda-b', productSlug: 'producto-b' }));
    fixture.detectChanges();
    expect((fixture.componentInstance as any).product()).toBeNull();
    expect(TestBed.inject(Title).getTitle()).toBe('Producto | Comercio Flex');
    expect(TestBed.inject(Meta).getTag('name="description"')?.content).not.toContain(
      'Descripción privada de A',
    );

    http.expectOne(
      '/api/v1/stores/tienda-b/catalog/products/producto-b',
    ).flush(
      { detail: 'Producto no encontrado.' },
      { status: 404, statusText: 'Not Found' },
    );
    fixture.detectChanges();
    expect(TestBed.inject(Title).getTitle()).toBe('Producto | Comercio Flex');
  });

  it('cancels an in-flight detail request when switching from tenant A to B', () => {
    create();
    const requestA = http.expectOne(
      '/api/v1/stores/tienda-a/catalog/products/remera-azul',
    );

    settings.set({
      slug: 'tienda-b',
      storeName: 'Tienda B',
      currencyCode: 'ARS',
      timezone: 'America/Argentina/Buenos_Aires',
    });
    params.next(convertToParamMap({ storeSlug: 'tienda-b', productSlug: 'producto-b' }));
    fixture.detectChanges();
    expect(requestA.cancelled).toBe(true);
    expect((fixture.componentInstance as any).product()).toBeNull();

    http.expectOne(
      '/api/v1/stores/tienda-b/catalog/products/producto-b',
    ).flush({
      id: 'product-b',
      name: 'Producto B',
      slug: 'producto-b',
      description: null,
      category: { id: 'category-b', name: 'General', slug: 'general' },
      variants: [{ id: 'variant-b', price: '50.00', size: null, color: null, available: true }],
    });
    fixture.detectChanges();
    expect(TestBed.inject(Title).getTitle()).toBe('Producto B | Tienda B');
  });
});
