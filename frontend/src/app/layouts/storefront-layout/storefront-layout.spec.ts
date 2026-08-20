import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { StorefrontLayout } from './storefront-layout';
import { CartPreviewService } from '../../features/storefront/cart/cart-preview.service';
import { CartService } from '../../features/storefront/cart/cart.service';

describe('StorefrontLayout', () => {
  let fixture: ComponentFixture<StorefrontLayout>;
  let http: HttpTestingController;

  beforeEach(async () => {
    const routeParams = new BehaviorSubject(convertToParamMap({ storeSlug: 'tienda-a' }));

    await TestBed.configureTestingModule({
      imports: [StorefrontLayout],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: routeParams.asObservable(),
            snapshot: { paramMap: routeParams.value },
          },
        },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(StorefrontLayout);
    fixture.detectChanges();
    http.expectOne('/api/v1/stores/tienda-a/settings').flush({
      slug: 'tienda-a',
      storeName: 'Tienda A',
      currencyCode: 'ARS',
      timezone: 'America/Argentina/Buenos_Aires',
      branding: {
        primaryColor: '#B7FF2A', secondaryColor: '#080808', backgroundColor: '#FFFFFF',
        textColor: '#0B0B0B', font: 'SANS', heroTitle: null, heroSubtitle: null,
        template: 'MODERN', logoUrl: null, faviconUrl: null, heroImageUrl: null,
      },
    });
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('keeps storefront section links inside the active store route', () => {
    const links = Array.from<HTMLAnchorElement>(
      fixture.nativeElement.querySelectorAll(
        'header nav a:not(.cart-link), .site-footer--streetwear div:nth-child(2) a',
      ),
    );

    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/tiendas/tienda-a',
      '/tiendas/tienda-a#catalog-products',
      '/tiendas/tienda-a#category-section',
      '/tiendas/tienda-a#catalog-products',
      '/tiendas/tienda-a#catalog-search',
      '/tiendas/tienda-a#catalog-products',
      '/tiendas/tienda-a#category-section',
      '/tiendas/tienda-a/carrito',
    ]);
  });

  it('shows the current tenant cart in an accessible preview panel', () => {
    TestBed.inject(CartService).add('tienda-a', {
      product: {
        id: 'product-1',
        name: 'Remera azul',
        slug: 'remera-azul',
        description: null,
        category: { id: 'category-1', name: 'Remeras', slug: 'remeras' },
        image: {
          id: 'image-1',
          url: '/media/image-1',
          thumbnailUrl: '/media/image-1/thumbnail',
          altText: 'Remera azul',
        },
        variants: [
          { id: 'variant-1', price: '2500.00', size: 'M', color: 'Azul', available: true },
        ],
      },
      variant: { id: 'variant-1', price: '2500.00', size: 'M', color: 'Azul', available: true },
      quantity: 2,
    });
    TestBed.inject(CartPreviewService).open('tienda-a');
    fixture.detectChanges();

    const preview: HTMLElement = fixture.nativeElement.querySelector('.cart-preview');
    expect(preview.getAttribute('role')).toBe('dialog');
    expect(preview.textContent).toContain('Artículos agregados a tu carrito');
    expect(preview.textContent).toContain('2 × Remera azul');
    expect(preview.textContent).toContain('Talle: M · Color: Azul');
    expect(preview.textContent).toContain('Ver carrito');
    expect(preview.querySelector('a')?.getAttribute('href')).toBe('/tiendas/tienda-a/carrito');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.cart-preview')).toBeNull();
  });
});
