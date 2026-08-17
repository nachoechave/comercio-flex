import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { StorefrontLayout } from './storefront-layout';

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
});
