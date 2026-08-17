import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { StorefrontApiService } from '../storefront-api.service';
import { StorefrontContextService } from '../storefront-context.service';
import { PublicProductDetail, StoreSettings } from '../storefront.models';
import { CartPage } from './cart-page';
import { CartService } from './cart.service';

const MODERN_BRANDING = {
  primaryColor: '#B7FF2A', secondaryColor: '#080808', backgroundColor: '#FFFFFF',
  textColor: '#0B0B0B', font: 'SANS' as const, heroTitle: null, heroSubtitle: null,
  template: 'MODERN' as const, logoUrl: null, faviconUrl: null, heroImageUrl: null,
};

describe('CartPage', () => {
  let fixture: ComponentFixture<CartPage>;
  let http: HttpTestingController;
  let cart: CartService;
  const params = new BehaviorSubject(convertToParamMap({ storeSlug: 'tienda-a' }));
  const settings = signal<StoreSettings | null>({
    slug: 'tienda-a',
    storeName: 'Tienda A',
    currencyCode: 'ARS',
    timezone: 'America/Argentina/Buenos_Aires',
    branding: MODERN_BRANDING,
  });
  const product: PublicProductDetail = {
    image: {
      id: 'image-1',
      url: '/media/image-1/original',
      thumbnailUrl: '/media/image-1/thumbnail',
      altText: 'Remera azul de frente',
    },
    id: 'product-1',
    name: 'Remera',
    slug: 'remera',
    description: null,
    category: { id: 'category-1', name: 'Remeras', slug: 'remeras' },
    variants: [
      {
        id: 'variant-1',
        price: '2500.00',
        size: 'M',
        color: 'Azul',
        available: true,
      },
    ],
  };

  beforeEach(async () => {
    localStorage.clear();
    params.next(convertToParamMap({ storeSlug: 'tienda-a' }));
    settings.set({
      slug: 'tienda-a',
      storeName: 'Tienda A',
      currencyCode: 'ARS',
      timezone: 'America/Argentina/Buenos_Aires',
      branding: MODERN_BRANDING,
    });
    await TestBed.configureTestingModule({
      imports: [CartPage],
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
    cart = TestBed.inject(CartService);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  function createWithLine(): void {
    cart.add('tienda-a', {
      product,
      variant: product.variants[0],
      quantity: 2,
    });
    fixture = TestBed.createComponent(CartPage);
    fixture.detectChanges();
  }

  it('revalidates a persisted line and updates price and metadata', () => {
    createWithLine();
    http.expectOne('/api/v1/stores/tienda-a/catalog/products/remera').flush({
      ...product,
      name: 'Remera nueva',
      variants: [{ ...product.variants[0], price: '2750.50' }],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Remera nueva');
    expect(fixture.nativeElement.textContent).toContain('$ 5.501,00');
    expect(fixture.nativeElement.textContent).toContain('Actualizamos los datos de este producto.');
    const page: HTMLElement = fixture.nativeElement.querySelector('.cart-page');
    const image: HTMLImageElement = fixture.nativeElement.querySelector('.line-visual img');
    expect(page.classList).toContain('cart-page--streetwear');
    expect(image.getAttribute('src')).toBe('/media/image-1/thumbnail');
    expect(image.alt).toBe('Remera azul de frente');
    expect(TestBed.inject(Title).getTitle()).toBe('Carrito | Tienda A');
    expect(TestBed.inject(Meta).getTag('name="description"')?.content).toContain('Tienda A');
  });

  it('marks a withdrawn product and excludes it from the actionable subtotal', () => {
    createWithLine();
    http
      .expectOne('/api/v1/stores/tienda-a/catalog/products/remera')
      .flush({ detail: 'No encontrado.' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Este producto ya no está publicado.');
    expect(fixture.nativeElement.textContent).toContain('$ 0,00');
    expect(cart.totalUnits('tienda-a')).toBe(2);
  });

  it('keeps snapshots on a network error and allows retry', () => {
    createWithLine();
    http
      .expectOne('/api/v1/stores/tienda-a/catalog/products/remera')
      .flush({ detail: 'Error.' }, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Podés reintentar sin perder el carrito');
    expect(cart.totalUnits('tienda-a')).toBe(2);

    const retry: HTMLButtonElement = fixture.nativeElement.querySelector('.warning button');
    retry.click();
    fixture.detectChanges();
    http.expectOne('/api/v1/stores/tienda-a/catalog/products/remera').flush(product);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain(
      'Podés reintentar sin perder el carrito',
    );
  });

  it('updates quantity, removes a line and renders the empty state', () => {
    createWithLine();
    http.expectOne('/api/v1/stores/tienda-a/catalog/products/remera').flush(product);
    fixture.detectChanges();

    const quantity: HTMLInputElement = fixture.nativeElement.querySelector('.line-actions input');
    quantity.value = '3';
    quantity.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(cart.totalUnits('tienda-a')).toBe(3);

    const remove: HTMLButtonElement = fixture.nativeElement.querySelector('.line-actions button');
    remove.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Todavía no elegiste productos');
    expect(cart.items('tienda-a')).toEqual([]);
  });
});
