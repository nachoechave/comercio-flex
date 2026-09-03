import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  ActivatedRoute,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import { of } from 'rxjs';

import { PaymentReturnStatus } from '../payment.models';
import { PaymentReturnPage } from './payment-return-page';

describe('PaymentReturnPage custom domain', () => {
  let fixture: ComponentFixture<PaymentReturnPage>;
  let http: HttpTestingController;

  const approved: PaymentReturnStatus = {
    orderId: 'order-1',
    orderNumber: 'PED-0001',
    orderStatus: 'CONFIRMED',
    paymentStatus: 'APPROVED',
    returnOutcome: null,
    canRetry: false,
    updatedAt: '2026-08-01T12:00:00Z',
  };

  beforeEach(async () => {
    sessionStorage.clear();

    const paramMap = convertToParamMap({
      returnToken: 'opaque-token',
    });

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
              paramMap,
              queryParamMap: convertToParamMap({}),
            },
            pathFromRoot: [
              {
                paramMap: of(paramMap),
              },
            ],
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
    sessionStorage.clear();
  });

  it('resolves the tenant from the custom domain before checking the payment', async () => {
    const resolution = http.expectOne('/api/v1/storefront/resolve');

    expect(resolution.request.method).toBe('GET');

    resolution.flush({
      storeSlug: 'la-ola-madre',
      displayName: 'La Ola Madre',
    });

    vi.advanceTimersByTime(0);

    http.expectOne('/api/v1/auth/csrf').flush({});

    const reconciliation = http.expectOne(
      '/api/v1/stores/la-ola-madre/payment-returns/opaque-token/reconcile',
    );

    expect(reconciliation.request.method).toBe('POST');

    reconciliation.flush(approved);

    fixture.detectChanges();
    await Promise.resolve();

    expect(fixture.nativeElement.textContent).toContain('Pago aprobado');
    expect(fixture.nativeElement.textContent).toContain('PED-0001');
  });
});