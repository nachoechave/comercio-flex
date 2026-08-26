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
import { CartService } from '../cart/cart.service';
import { CartPreviewService } from '../cart/cart-preview.service';
import { PublicProductDetail } from './public-product-detail';

const MODERN_BRANDING = {
  primaryColor: '#B7FF2A',
  secondaryColor: '#080808',
  backgroundColor: '#FFFFFF',
  textColor: '#0B0B0B',
  font: 'SANS' as const,
  heroTitle: null,
  heroSubtitle: null,
  template: 'MODERN' as const,
  logoUrl: null,
  faviconUrl: null,
  heroImageUrl: null,
};

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
    branding: MODERN_BRANDING,
  });

  beforeEach(async () => {
    localStorage.clear();
    params.next(convertToParamMap({ storeSlug: 'tienda-a', productSlug: 'remera-azul' }));
    settings.set({
      slug: 'tienda-a',
      storeName: 'Tienda A',
      currencyCode: 'ARS',
      timezone: 'America/Argentina/Buenos_Aires',
      branding: MODERN_BRANDING,
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

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  function create(): void {
    fixture = TestBed.createComponent(PublicProductDetail);
    fixture.detectChanges();
  }

  it('renders public variants and updates the document title', () => {
    create();
    http.expectOne('/api/v1/stores/tienda-a/catalog/products/remera-azul').flush({
      id: 'product-1',
      name: 'Remera azul',
      slug: 'remera-azul',
      description: 'Algodón suave.',
      category: { id: 'category-1', name: 'Remeras', slug: 'remeras' },
      image: {
        id: 'image-1',
        url: '/media/image-1',
        thumbnailUrl: '/media/image-1/thumbnail',
        altText: 'Remera azul sobre fondo claro',
      },
      variants: [
        { id: 'variant-1', price: '2500.00', size: 'M', color: 'Azul', available: true },
        { id: 'variant-2', price: '2600.00', size: 'L', color: 'Azul', available: false },
      ],
    });
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Remera azul');
    expect(text).toContain('Talle: M · Color: Azul');
    expect(text).toContain('Talle: L · Color: Azul');
    expect(text).toContain('Sin stock');
    expect(TestBed.inject(Title).getTitle()).toBe('Remera azul | Tienda A');
    expect(fixture.nativeElement.querySelector('.product-page--streetwear')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.featured-price').textContent).toContain('$');
    expect(fixture.nativeElement.querySelectorAll('.purchase-benefits li')).toHaveLength(3);
    const image: HTMLImageElement = fixture.nativeElement.querySelector('.product-hero img');
    expect(image.getAttribute('src')).toBe('/media/image-1');
    expect(image.alt).toBe('Remera azul sobre fondo claro');
  });

  it('requires an explicit available variant and adds it to the tenant cart', () => {
    create();
    http.expectOne('/api/v1/stores/tienda-a/catalog/products/remera-azul').flush({
      id: 'product-1',
      name: 'Remera azul',
      slug: 'remera-azul',
      description: null,
      category: { id: 'category-1', name: 'Remeras', slug: 'remeras' },
      variants: [
        { id: 'variant-1', price: '2500.00', size: 'M', color: 'Azul', available: true },
        { id: 'variant-2', price: '2600.00', size: 'L', color: 'Azul', available: false },
      ],
    });
    fixture.detectChanges();

    const addButton: HTMLButtonElement =
      fixture.nativeElement.querySelector('.purchase-panel button');
    const radios: NodeListOf<HTMLInputElement> =
      fixture.nativeElement.querySelectorAll('input[type="radio"]');
    expect(addButton.disabled).toBe(true);
    expect(radios[1].disabled).toBe(true);

    radios[0].dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(addButton.disabled).toBe(false);

    const quantity: HTMLInputElement = fixture.nativeElement.querySelector(
      '.purchase-panel input[type="number"]',
    );
    quantity.value = '2';
    quantity.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    addButton.click();
    fixture.detectChanges();

    expect(TestBed.inject(CartService).totalUnits('tienda-a')).toBe(2);
    expect(TestBed.inject(CartPreviewService).storeSlug()).toBe('tienda-a');
    expect(fixture.nativeElement.textContent).toContain('Agregamos 2 unidades al carrito');
    expect(fixture.nativeElement.textContent).toContain('Ver carrito');
  });

  it('selects the exact generic-option variant and keeps availability independent', () => {
    create();
    http.expectOne('/api/v1/stores/tienda-a/catalog/products/remera-azul').flush({
      id: 'product-1',
      name: 'Remera azul',
      slug: 'remera-azul',
      description: null,
      category: { id: 'category-1', name: 'Remeras', slug: 'remeras' },
      variants: [
        {
          id: 'variant-m-negro',
          price: '2500.00',
          size: 'M',
          color: 'Negro',
          options: [
            { name: 'Talle', value: 'M' },
            { name: 'Color', value: 'Negro' },
          ],
          available: false,
        },
        {
          id: 'variant-l-negro',
          price: '2700.00',
          size: 'L',
          color: 'Negro',
          options: [
            { name: 'Talle', value: 'L' },
            { name: 'Color', value: 'Negro' },
          ],
          available: true,
        },
      ],
    });
    fixture.detectChanges();

    const radios: NodeListOf<HTMLInputElement> =
      fixture.nativeElement.querySelectorAll('input[type="radio"]');
    expect(radios[0].disabled).toBe(true);
    expect(radios[1].disabled).toBe(false);
    radios[1].dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.featured-price').textContent).toContain('2.700');

    const addButton: HTMLButtonElement =
      fixture.nativeElement.querySelector('.purchase-panel button');
    addButton.click();
    expect(TestBed.inject(CartService).items('tienda-a')[0]).toMatchObject({
      variantId: 'variant-l-negro',
      unitPrice: '2700.00',
      options: [
        { name: 'Talle', value: 'L' },
        { name: 'Color', value: 'Negro' },
      ],
    });
  });

  it('renders a product-specific not-found state', () => {
    create();
    http
      .expectOne('/api/v1/stores/tienda-a/catalog/products/remera-azul')
      .flush({ detail: 'Producto no encontrado.' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No encontramos este producto');
    expect(fixture.nativeElement.textContent).toContain('Volver al catálogo');
    expect(fixture.nativeElement.textContent).not.toContain('Reintentar');
  });

  it('keeps neutral metadata until settings arrive and then updates reactively', () => {
    settings.set(null);
    create();
    http.expectOne('/api/v1/stores/tienda-a/catalog/products/remera-azul').flush({
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
    http.expectOne('/api/v1/stores/tienda-a/catalog/products/remera-azul').flush({
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

    http
      .expectOne('/api/v1/stores/tienda-b/catalog/products/producto-b')
      .flush({ detail: 'Producto no encontrado.' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(TestBed.inject(Title).getTitle()).toBe('Producto | Comercio Flex');
  });

  it('cancels an in-flight detail request when switching from tenant A to B', () => {
    create();
    const requestA = http.expectOne('/api/v1/stores/tienda-a/catalog/products/remera-azul');

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

    http.expectOne('/api/v1/stores/tienda-b/catalog/products/producto-b').flush({
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
