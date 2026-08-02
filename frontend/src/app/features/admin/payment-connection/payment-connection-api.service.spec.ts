import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PaymentConnectionApiService } from './payment-connection-api.service';

describe('PaymentConnectionApiService', () => {
  let service: PaymentConnectionApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PaymentConnectionApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the payment connection from the encoded store URL', () => {
    service.get('tienda/a').subscribe();
    const request = http.expectOne('/api/v1/stores/tienda%2Fa/admin/payment-connection');
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('starts authorization without sending provider data from the browser', () => {
    service.startAuthorization('tienda-a').subscribe();
    const request = http.expectOne(
      '/api/v1/stores/tienda-a/admin/payment-connection/authorization',
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({});
  });

  it('disconnects through the explicit connection resource', () => {
    service.disconnect('tienda-a').subscribe();
    const request = http.expectOne('/api/v1/stores/tienda-a/admin/payment-connection');
    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toBeNull();
    request.flush(null);
  });

  it('loads only failed or exhausted webhooks from the safe operational endpoint', () => {
    service.getFailedWebhooks('tienda/a').subscribe();

    const request = http.expectOne(
      '/api/v1/stores/tienda%2Fa/admin/payment-webhooks?status=DEAD',
    );
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('schedules an explicit webhook retry without sending operational data', () => {
    service.retryWebhook('tienda-a', 'event/42').subscribe();

    const request = http.expectOne(
      '/api/v1/stores/tienda-a/admin/payment-webhooks/event%2F42/retry',
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({
      eventId: 'event/42',
      status: 'RETRY_SCHEDULED',
      scheduledAt: '2026-08-01T12:00:00Z',
    });
  });
});
