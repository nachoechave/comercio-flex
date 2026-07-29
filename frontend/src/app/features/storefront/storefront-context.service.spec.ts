import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StorefrontApiService } from './storefront-api.service';
import { StorefrontContextService } from './storefront-context.service';

describe('StorefrontContextService', () => {
  let context: StorefrontContextService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        StorefrontApiService,
        StorefrontContextService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    context = TestBed.inject(StorefrontContextService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('cleans stale settings and cancels the previous request when tenant changes', () => {
    context.load('tienda-a');
    const requestA = http.expectOne('/api/v1/stores/tienda-a/settings');

    context.load('tienda-b');
    expect(requestA.cancelled).toBe(true);
    expect(context.settings()).toBeNull();

    http.expectOne('/api/v1/stores/tienda-b/settings').flush({
      slug: 'tienda-b',
      storeName: 'Tienda B',
      currencyCode: 'ARS',
      timezone: 'America/Argentina/Buenos_Aires',
    });

    expect(context.settings()?.slug).toBe('tienda-b');
    expect(context.loading()).toBe(false);
  });

  it('marks an unknown store as not found and allows an explicit retry', () => {
    context.load('no-existe');
    http.expectOne('/api/v1/stores/no-existe/settings').flush(
      { detail: 'Comercio no encontrado.' },
      { status: 404, statusText: 'Not Found' },
    );
    expect(context.notFound()).toBe(true);
    expect(context.errorMessage()).toContain('Comercio no encontrado');

    context.retry();
    http.expectOne('/api/v1/stores/no-existe/settings').flush({
      slug: 'no-existe',
      storeName: 'Nueva tienda',
      currencyCode: 'ARS',
      timezone: 'America/Argentina/Buenos_Aires',
    });
    expect(context.notFound()).toBe(false);
  });
});
