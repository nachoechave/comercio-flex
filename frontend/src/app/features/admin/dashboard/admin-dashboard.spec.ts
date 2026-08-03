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

  it('renders the tenant metrics and critical stock list', () => {
    http.expectOne('/api/v1/stores/tienda-a/admin/dashboard').flush(SUMMARY);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent.replace(/\s+/g, ' ');
    expect(text).toContain('Ventas de hoy');
    expect(text).toContain('16.900,75');
    expect(text).toContain('Pedidos abiertos2');
    expect(text).toContain('Asado especial');
    expect(text).toContain('1.250');
  });

  it('updates the configurable threshold after obtaining CSRF', () => {
    http.expectOne('/api/v1/stores/tienda-a/admin/dashboard').flush(SUMMARY);
    fixture.detectChanges();
    fixture.componentInstance.thresholdForm.controls.threshold.setValue('2.500');
    fixture.componentInstance.saveThreshold();

    http.expectOne('/api/v1/auth/csrf').flush({ token: 'csrf' });
    const update = http.expectOne('/api/v1/stores/tienda-a/admin/dashboard/settings');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({ lowStockThreshold: '2.500' });
    update.flush({ ...SUMMARY, lowStockThreshold: '2.500' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Actualizamos el umbral');
  });

  it('shows a retryable error when loading fails', () => {
    http
      .expectOne('/api/v1/stores/tienda-a/admin/dashboard')
      .flush({}, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('No pudimos cargar');
  });
});
