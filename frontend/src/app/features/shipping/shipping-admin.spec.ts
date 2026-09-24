import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { ShippingSettingsPage } from './shipping-settings-page';
import { ShipmentPanel } from './shipment-panel';

describe('Shipping administration', () => {
  let http: HttpTestingController;
  const settingsUrl = '/api/v1/stores/tienda-a/admin/shipping';
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ShippingSettingsPage, ShipmentPanel],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({ storeSlug: 'tienda-a' })),
            snapshot: { paramMap: convertToParamMap({ storeSlug: 'tienda-a' }) },
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('loads editable tariffs and saves through CSRF with configuration version', () => {
    const fixture = TestBed.createComponent(ShippingSettingsPage);
    fixture.detectChanges();
    http.expectOne(settingsUrl).flush({ version: 3, freeShippingThreshold: 50000, methods: [] });
    fixture.componentInstance.add();
    fixture.componentInstance.settings!.methods[0].name = 'Envío';
    fixture.componentInstance.save();
    http.expectOne('/api/v1/auth/csrf').flush({});
    const save = http.expectOne(settingsUrl);
    expect(save.request.method).toBe('PUT');
    expect(save.request.body.version).toBe(3);
    expect(save.request.body.methods[0].name).toBe('Envío');
    save.flush({ ...save.request.body, version: 4 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Configuración guardada');
  });
  it('shows a concurrent configuration error without losing edits', () => {
    const fixture = TestBed.createComponent(ShippingSettingsPage);
    fixture.detectChanges();
    http.expectOne(settingsUrl).flush({ version: 3, freeShippingThreshold: null, methods: [] });
    fixture.componentInstance.save();
    http.expectOne('/api/v1/auth/csrf').flush({});
    http
      .expectOne(settingsUrl)
      .flush({ message: 'La configuración cambió' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('La configuración cambió');
    expect(fixture.componentInstance.settings!.version).toBe(3);
  });
  it('loads tracking, restricts status options and saves dispatch', () => {
    const fixture = TestBed.createComponent(ShipmentPanel);
    fixture.componentRef.setInput('storeSlug', 'tienda-a');
    fixture.componentRef.setInput('orderId', 'order-1');
    fixture.detectChanges();
    const url = '/api/v1/stores/tienda-a/admin/orders/order-1/shipment';
    http.expectOne(url).flush({
      id: 'shipment-1',
      orderId: 'order-1',
      status: 'PREPARING',
      version: 1,
      carrierName: null,
      trackingNumber: null,
      trackingUrl: null,
      notes: null,
    });
    expect(fixture.componentInstance.allowed()).toEqual(['PREPARING', 'SHIPPED', 'CANCELLED']);
    Object.assign(fixture.componentInstance.shipment!, {
      status: 'SHIPPED',
      carrierName: 'Transporte',
      trackingNumber: '123',
    });
    fixture.componentInstance.save();
    http.expectOne('/api/v1/auth/csrf').flush({});
    const save = http.expectOne(url);
    expect(save.request.method).toBe('PUT');
    expect(save.request.body.trackingNumber).toBe('123');
    save.flush({ ...save.request.body, version: 2 });
    fixture.detectChanges();
    expect(fixture.componentInstance.allowed()).toEqual(['SHIPPED', 'DELIVERED']);
    expect(fixture.nativeElement.textContent).toContain('Envío actualizado');
  });
});
