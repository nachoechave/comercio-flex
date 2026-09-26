import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { StoreAnalyticsService } from './store-analytics.service';

describe('StoreAnalyticsService', () => {
  let service: StoreAnalyticsService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    window.history.replaceState({}, '', '/');
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(StoreAnalyticsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    service.deactivate();
    http.verify();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('tracks a page view without persisting query parameters', () => {
    window.history.replaceState(
      {},
      '',
      '/tiendas/tienda-a?utm_source=instagram&utm_medium=social&token=secret',
    );

    service.activate('tienda-a');

    const request = http.expectOne('/api/v1/stores/tienda-a/analytics/events');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.eventType).toBe('PAGE_VIEW');
    expect(request.request.body.path).toBe('/tiendas/tienda-a');
    expect(request.request.body.source).toBe('instagram');
    expect(request.request.body.medium).toBe('social');
    expect(request.request.body.path).not.toContain('secret');
    request.flush(null);
  });

  it('uses different anonymous visitor ids for different tenants', () => {
    window.history.replaceState({}, '', '/tiendas/tienda-a');
    service.activate('tienda-a');
    const first = http.expectOne('/api/v1/stores/tienda-a/analytics/events');
    const firstVisitor = first.request.body.visitorId as string;
    first.flush(null);
    service.deactivate('tienda-a');

    window.history.replaceState({}, '', '/tiendas/tienda-b');
    service.activate('tienda-b');
    const second = http.expectOne('/api/v1/stores/tienda-b/analytics/events');
    const secondVisitor = second.request.body.visitorId as string;
    second.flush(null);

    expect(firstVisitor).not.toBe(secondVisitor);
  });

  it('does not track admin template previews', () => {
    window.history.replaceState(
      {},
      '',
      '/tiendas/tienda-a?preview=1&previewTemplate=FRESH',
    );

    service.activate('tienda-a');

    http.expectNone('/api/v1/stores/tienda-a/analytics/events');
  });

  it('loads a tenant summary for the selected period', () => {
    service.summary('tienda-a', 30).subscribe();

    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/stores/tienda-a/admin/analytics' &&
        candidate.params.get('days') === '30',
    );
    expect(request.request.method).toBe('GET');
    request.flush({
      days: 30,
      timezone: 'America/Argentina/Buenos_Aires',
      from: '2026-08-27T03:00:00Z',
      to: '2026-09-26T03:00:00Z',
      visits: 10,
      visitors: 8,
      pageViews: 20,
      productViews: 7,
      addToCarts: 3,
      checkouts: 2,
      purchases: 1,
      conversionRate: 10,
      topProducts: [],
      trafficSources: [],
      generatedAt: '2026-09-25T23:30:00Z',
    });
  });
});
