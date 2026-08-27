import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { AdminDashboard } from './admin-dashboard';
import { DashboardSummary } from './dashboard.models';

const SUMMARY: DashboardSummary = {
  currencyCode: 'ARS',
  timezone: 'America/Argentina/Buenos_Aires',
  lowStockThreshold: '5.000',
  salesToday: '16900.75',
  salesThisMonth: '33801.50',
  openOrders: 2,
  lowStockVariants: 1,
  criticalStock: [
    {
      variantId: 'variant-1',
      productName: 'Asado especial',
      sku: 'ASA-1KG',
      size: null,
      color: null,
      quantity: '1.250',
    },
  ],
  generatedAt: '2026-08-03T15:00:00Z',
};

describe('AdminDashboard', () => {
  let fixture: ComponentFixture<AdminDashboard>;
  let http: HttpTestingController;

  beforeEach(async () => {
    const params = new BehaviorSubject(convertToParamMap({ storeSlug: 'tienda-a' }));
    await TestBed.configureTestingModule({
      imports: [AdminDashboard],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            pathFromRoot: [{ paramMap: params.asObservable() }],
            snapshot: { paramMap: convertToParamMap({}) },
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdminDashboard);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function flushSupportingData(): void {
    http.expectOne((request) => request.url.endsWith('/admin/orders') && request.params.get('size') === '5').flush({
      items: [{ id: 'order-1', number: 'ORD-1', status: 'CONFIRMED', fulfillmentType: 'PICKUP', customerName: 'Ana', customerPhone: '111', currencyCode: 'ARS', subtotal: '2500.00', createdAt: '2026-08-25T18:05:00Z' }],
      page: 0, size: 5, totalItems: 1, totalPages: 1,
    });
    http.expectOne('/api/v1/stores/tienda-a/admin/bank-transfer-payments').flush([
      { id: 'payment-1', orderId: 'order-1', orderNumber: 'ORD-1', customerName: 'Ana', amount: '2500.00', currencyCode: 'ARS', attemptNumber: 1, status: 'UNDER_REVIEW', receiptAvailable: true, receiptOriginalFilename: 'pago.pdf', receiptContentType: 'application/pdf', receiptSize: 100, receiptUploadedAt: '2026-08-25T18:05:00Z', reservationExpiresAt: '2026-08-25T19:05:00Z', reviewedAt: null, rejectionReason: null, createdAt: '2026-08-25T18:05:00Z', updatedAt: '2026-08-25T18:05:00Z' },
    ]);
  }

  it('renders the tenant metrics and critical stock list', () => {
    http.expectOne('/api/v1/stores/tienda-a/admin/dashboard').flush(SUMMARY);
    flushSupportingData();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent.replace(/\s+/g, ' ');
    expect(text).toContain('Ventas de hoy');
    expect(text).toContain('16.900,75');
    expect(text).toContain('Pedidos abiertos2');
    expect(text).toContain('Asado especial');
    expect(text).toContain('Umbral: 5');
    expect(text).toContain('1.25');
    expect(text).toContain('Pedidos recientes');
    expect(text).toContain('Transferencias a revisar');
    expect(text).toContain('ORD-1');
  });

  it('links inventory threshold configuration outside the dashboard', () => {
    http.expectOne('/api/v1/stores/tienda-a/admin/dashboard').flush(SUMMARY);
    flushSupportingData();
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector(
      '[data-testid="inventory-threshold-settings-link"]',
    ) as HTMLAnchorElement;
    expect(link).toBeTruthy();
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  it('shows a retryable error when loading fails', () => {
    flushSupportingData();
    http.expectOne('/api/v1/stores/tienda-a/admin/dashboard').flush({}, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('No pudimos cargar');
  });
});
