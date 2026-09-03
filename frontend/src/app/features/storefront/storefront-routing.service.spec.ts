import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRoute,
  convertToParamMap,
} from '@angular/router';
import {
  firstValueFrom,
  of,
  throwError,
} from 'rxjs';

import { StorefrontApiService } from './storefront-api.service';
import { StorefrontRoutingService } from './storefront-routing.service';

describe('StorefrontRoutingService', () => {
  let service: StorefrontRoutingService;
  let api: {
    resolveStorefront: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    api = {
      resolveStorefront: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        StorefrontRoutingService,
        {
          provide: StorefrontApiService,
          useValue: api,
        },
      ],
    });

    service = TestBed.inject(StorefrontRoutingService);
  });

  function routeWithParams(
    params: Record<string, string> = {},
  ): ActivatedRoute {
    const paramMap = convertToParamMap(params);

    return {
      pathFromRoot: [
        {
          paramMap: of(paramMap),
        },
      ],
      snapshot: {
        paramMap,
      },
    } as unknown as ActivatedRoute;
  }

  it('keeps the legacy store slug route on the Comercio Flex platform', async () => {
    const route = routeWithParams({
      storeSlug: 'tienda-a',
    });

    const storeSlug = await firstValueFrom(
      service.storeSlug(route),
    );

    expect(storeSlug).toBe('tienda-a');

    expect(
      service.route('tienda-a', 'carrito'),
    ).toEqual([
      '/tiendas',
      'tienda-a',
      'carrito',
    ]);

    expect(api.resolveStorefront).not.toHaveBeenCalled();
  });

  it('resolves a custom domain and generates storefront root routes', async () => {
    api.resolveStorefront.mockReturnValue(
      of({
        storeSlug: 'la-ola-madre',
        displayName: 'La Ola Madre',
      }),
    );

    const customDomain = await firstValueFrom(
      service.resolveCustomDomain(),
    );

    expect(customDomain).toBe(true);

    const storeSlug = await firstValueFrom(
      service.storeSlug(routeWithParams()),
    );

    expect(storeSlug).toBe('la-ola-madre');

    expect(
      service.route('la-ola-madre', 'carrito'),
    ).toEqual([
      '/',
      'carrito',
    ]);

    expect(
      service.route(
        'la-ola-madre',
        'productos',
        'remera-surf',
      ),
    ).toEqual([
      '/',
      'productos',
      'remera-surf',
    ]);
  });

  it('treats a 404 resolver response as a platform hostname', async () => {
    api.resolveStorefront.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 404,
            statusText: 'Not Found',
          }),
      ),
    );

    const customDomain = await firstValueFrom(
      service.resolveCustomDomain(),
    );

    expect(customDomain).toBe(false);

    expect(
      service.route('tienda-a', 'carrito'),
    ).toEqual([
      '/tiendas',
      'tienda-a',
      'carrito',
    ]);
  });

  it('does not hide unexpected storefront resolver failures', async () => {
    api.resolveStorefront.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 500,
            statusText: 'Internal Server Error',
          }),
      ),
    );

    await expect(
      firstValueFrom(service.resolveCustomDomain()),
    ).rejects.toMatchObject({
      status: 500,
    });
  });
});