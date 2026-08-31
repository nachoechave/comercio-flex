import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { PaymentReturnPage } from './payment-return-page';

describe('PaymentReturnPage without a provider payment', () => {
  let fixture: ComponentFixture<PaymentReturnPage>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaymentReturnPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({
                storeSlug: 'tienda-a',
                returnToken: 'opaque-token',
              }),
              queryParamMap: convertToParamMap({ payment: 'failed' }),
            },
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    vi.useFakeTimers();
    fixture = TestBed.createComponent(PaymentReturnPage);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
    fixture.destroy();
    http.verify();
  });

  it('verifies the preference and explains that no charge was recorded', async () => {
    vi.advanceTimersByTime(0);
    http.expectOne('/api/v1/auth/csrf').flush({});
	const inspection = http.expectOne(
	  '/api/v1/stores/tienda-a/payment-returns/opaque-token/reconcile',
	);
    expect(inspection.request.method).toBe('POST');
    inspection.flush({
      orderId: 'order-12',
      orderNumber: 12,
      orderStatus: 'PENDING_CONFIRMATION',
      paymentStatus: 'PENDING',
      returnOutcome: 'PAYMENT_NOT_RECORDED',
      canRetry: true,
      updatedAt: '2026-08-02T22:16:00Z',
    });
    fixture.detectChanges();
    await Promise.resolve();

    expect(fixture.nativeElement.textContent).toContain('Pago no completado');
    expect(fixture.nativeElement.textContent).toContain(
      'Mercado Pago no registró ningún cobro',
    );
    vi.advanceTimersByTime(6_000);
    http.expectNone('/api/v1/stores/tienda-a/payment-returns/opaque-token');
  });
});
