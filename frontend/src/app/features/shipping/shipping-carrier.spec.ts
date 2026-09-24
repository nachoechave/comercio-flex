import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { CarrierSettingsPage } from './carrier-settings-page';
import { ShipmentPanel } from './shipment-panel';
import { ShippingSelector } from './shipping-selector';
import { ShippingQuote } from './shipping.models';

describe('Carrier shipping phase 2', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CarrierSettingsPage, ShipmentPanel, ShippingSelector],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
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

  it('saves Andreani credentials without requiring the stored secret back from the API', () => {
    const fixture = TestBed.createComponent(CarrierSettingsPage);
    fixture.detectChanges();
    const url = '/api/v1/stores/tienda-a/admin/shipping/carrier';
    http.expectOne(url).flush({
      provider: 'ANDREANI',
      enabled: false,
      environment: 'SANDBOX',
      clientCode: null,
      contractCode: null,
      credentialsConfigured: false,
      origin: null,
      defaultParcel: null,
      trackingSyncMinutes: 15,
      version: 0,
    });

    fixture.componentInstance.draft!.clientCode = 'CLIENTE';
    fixture.componentInstance.draft!.contractCode = 'CONTRATO';
    fixture.componentInstance.draft!.origin.senderName = 'Tienda';
    fixture.componentInstance.draft!.origin.senderEmail = 'tienda@example.com';
    fixture.componentInstance.draft!.origin.senderPhone = '2210000000';
    fixture.componentInstance.draft!.origin.senderDocumentNumber = '30700000001';
    fixture.componentInstance.draft!.origin.street = 'Calle';
    fixture.componentInstance.draft!.origin.number = '123';
    fixture.componentInstance.draft!.origin.postalCode = '1925';
    fixture.componentInstance.draft!.origin.city = 'Ensenada';
    fixture.componentInstance.draft!.origin.province = 'Buenos Aires';
    fixture.componentInstance.username = 'usuario';
    fixture.componentInstance.password = 'secreto';
    fixture.componentInstance.save();

    http.expectOne('/api/v1/auth/csrf').flush({});
    const save = http.expectOne(url);
    expect(save.request.method).toBe('PUT');
    expect(save.request.body.username).toBe('usuario');
    expect(save.request.body.password).toBe('secreto');
    save.flush({
      ...save.request.body,
      username: undefined,
      password: undefined,
      credentialsConfigured: true,
      version: 1,
    });
    fixture.detectChanges();

    expect(fixture.componentInstance.username).toBe('');
    expect(fixture.componentInstance.password).toBe('');
    expect(fixture.componentInstance.credentialsConfigured()).toBe(true);
  });

  it('requires recipient document before selecting a live carrier quote', () => {
    const fixture = TestBed.createComponent(ShippingSelector);
    fixture.componentRef.setInput('storeSlug', 'tienda-a');
    fixture.componentRef.setInput('items', [{ variantId: 'variant-1', quantity: '1' }]);
    fixture.componentRef.setInput('paymentMethod', 'MERCADO_PAGO');
    fixture.detectChanges();
    fixture.componentInstance.setMode('SHIPPING');
    fixture.componentInstance.address.setValue({
      street: 'Calle',
      number: '1',
      apartment: '',
      city: 'La Plata',
      province: 'Buenos Aires',
      postalCode: '1900',
    });

    const carrier: ShippingQuote = {
      methodId: '7f3c7217-4e8e-4a8c-9fd6-bbe52e8a8d21',
      name: 'Andreani a domicilio',
      description: 'Entrega Andreani',
      type: 'CARRIER',
      shippingAmount: '3000.00',
      freeShipping: false,
      listSubtotal: '10000.00',
      discountAmount: '0.00',
      subtotal: '10000.00',
      total: '13000.00',
      pickupAddress: null,
      instructions: null,
      provider: 'ANDREANI',
      serviceCode: 'ANDREANI_DOMICILIO',
      estimatedDays: 2,
      quoteToken: '11111111-1111-4111-8111-111111111111',
    };

    fixture.componentInstance.quote();
    http.expectOne('/api/v1/stores/tienda-a/shipping/quote').flush([carrier]);
    fixture.detectChanges();
    fixture.componentInstance.select(carrier);
    expect(fixture.componentInstance.current()).toBeNull();

    fixture.componentInstance.carrierDocument.setValue({ type: 'DNI', number: '41131132' });
    const emitted = vi.fn();
    fixture.componentInstance.selection.subscribe(emitted);
    fixture.componentInstance.select(carrier);
    expect(emitted).toHaveBeenLastCalledWith(
      expect.objectContaining({
        quoteToken: carrier.quoteToken,
        documentType: 'DNI',
        documentNumber: '41131132',
      }),
    );
  });

  it('provisions Andreani and exposes the protected PDF label endpoint', () => {
    const fixture = TestBed.createComponent(ShipmentPanel);
    fixture.componentRef.setInput('storeSlug', 'tienda-a');
    fixture.componentRef.setInput('orderId', 'order-1');
    fixture.detectChanges();
    const url = '/api/v1/stores/tienda-a/admin/orders/order-1/shipment';
    http.expectOne(url).flush({
      id: 'shipment-1',
      orderId: 'order-1',
      provider: 'ANDREANI',
      status: 'PENDING',
      carrierName: null,
      trackingNumber: null,
      trackingUrl: null,
      externalReference: null,
      labelReference: null,
      notes: null,
      version: 0,
    });

    fixture.componentInstance.provision();
    http.expectOne('/api/v1/auth/csrf').flush({});
    const provision = http.expectOne(url + '/carrier');
    expect(provision.request.method).toBe('POST');
    provision.flush({
      ...fixture.componentInstance.shipment,
      provider: 'ANDREANI',
      carrierName: 'Andreani',
      externalReference: '360000000000001',
      labelReference: 'AGROUP-1',
      trackingNumber: '360000000000001',
      version: 1,
    });
    fixture.detectChanges();

    expect(fixture.componentInstance.labelUrl()).toBe(url + '/label');
    expect(fixture.nativeElement.textContent).toContain('Descargar etiqueta PDF');
  });
});
