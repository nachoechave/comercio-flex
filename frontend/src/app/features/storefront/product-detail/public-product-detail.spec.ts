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

  it('renders the responsive product layout, image, price and generic option selectors', () => {
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
    expect(text).toContain('Talle');
    expect(text).toContain('Color');
    expect(text).toContain('M');
    expect(text).toContain('L');
    expect(text).toContain('Algodón suave.');
    expect(TestBed.inject(Title).getTitle()).toBe('Remera azul | Tienda A');
    expect(fixture.nativeElement.querySelector('.product-page--streetwear')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.product-layout')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.product-media')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.product-purchase')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.quantity-control')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.featured-price').textContent).toContain('$');
    expect(fixture.nativeElement.querySelectorAll('.purchase-benefits li')).toHaveLength(2);
    expect(text).not.toContain('Envíos a todo el país');
    expect(text).not.toContain('Cambios simples');
    expect(fixture.nativeElement.querySelector('.product-information')).not.toBeNull();
    const image: HTMLImageElement = fixture.nativeElement.querySelector('.product-media img');
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

    const addButton: HTMLButtonElement = fixture.nativeElement.querySelector('.add-to-cart');
    const chips = Array.from<HTMLButtonElement>(
      fixture.nativeElement.querySelectorAll('.option-chip'),
    );
    const sizeM = chips.find((button) => button.textContent?.trim() === 'M')!;
    const sizeL = chips.find((button) => button.textContent?.trim() === 'L')!;
    const colorBlue = chips.find((button) => button.textContent?.trim() === 'Azul')!;
    expect(addButton.disabled).toBe(true);
    expect(sizeL.disabled).toBe(true);

    sizeM.click();
    colorBlue.click();
    fixture.detectChanges();
    expect(addButton.disabled).toBe(false);

    const increaseButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[aria-label="Aumentar cantidad"]',
    );
    const decreaseButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[aria-label="Disminuir cantidad"]',
    );
    const quantity: HTMLInputElement =
      fixture.nativeElement.querySelector('.quantity-control input');
    increaseButton.click();
    fixture.detectChanges();
    expect(quantity.value).toBe('2');
    decreaseButton.click();
    fixture.detectChanges();
    expect(quantity.value).toBe('1');
    quantity.value = '2';
    quantity.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    addButton.click();
    fixture.detectChanges();

    expect(TestBed.inject(CartService).totalUnits('tienda-a')).toBe(2);
    expect(TestBed.inject(CartService).totalUnits('tienda-b')).toBe(0);
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

    const chips = Array.from<HTMLButtonElement>(
      fixture.nativeElement.querySelectorAll('.option-chip'),
    );
    const sizeM = chips.find((button) => button.textContent?.trim() === 'M')!;
    const sizeL = chips.find((button) => button.textContent?.trim() === 'L')!;
    const colorBlack = chips.find((button) => button.textContent?.trim() === 'Negro')!;
    expect(sizeM.disabled).toBe(true);
    expect(sizeL.disabled).toBe(false);
    expect(colorBlack.disabled).toBe(false);
    sizeL.click();
    colorBlack.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.featured-price').textContent).toContain('2.700');

    const addButton: HTMLButtonElement = fixture.nativeElement.querySelector('.add-to-cart');
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

  it('auto-selects a simple product and handles missing image and description without invented copy', () => {
    create();
    http.expectOne('/api/v1/stores/tienda-a/catalog/products/remera-azul').flush({
      id: 'product-simple',
      name: 'Producto simple',
      slug: 'remera-azul',
      description: null,
      category: { id: 'category-1', name: 'General', slug: 'general' },
      image: null,
      variants: [
        { id: 'variant-simple', price: '1250.00', size: null, color: null, available: true },
      ],
    });
    fixture.detectChanges();

    const addButton: HTMLButtonElement = fixture.nativeElement.querySelector('.add-to-cart');
    expect(addButton.disabled).toBe(false);
    expect(fixture.nativeElement.querySelector('.simple-variant').textContent).toContain(
      'Opción estándar',
    );
    expect(fixture.nativeElement.querySelector('.visual img')).toBeNull();
    expect(fixture.nativeElement.querySelector('.visual span').textContent.trim()).toBe('P');
    expect(fixture.nativeElement.querySelector('.product-information')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain(
      'Consultá las opciones y precios disponibles',
    );
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
