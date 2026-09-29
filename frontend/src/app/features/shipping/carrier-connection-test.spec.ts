import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { CarrierSettingsPage } from './carrier-settings-page';

describe('Andreani connection test', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CarrierSettingsPage],
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

  it('tests stored credentials and renders authentication plus quote diagnostics', () => {
    const fixture = TestBed.createComponent(CarrierSettingsPage);
    fixture.detectChanges();
    const baseUrl = '/api/v1/stores/tienda-a/admin/shipping/carrier';
    http.expectOne(baseUrl).flush({
      provider: 'ANDREANI',
      enabled: false,
      environment: 'SANDBOX',
      clientCode: 'CLIENTE',
      contractCode: 'CONTRATO',
      credentialsConfigured: true,
      origin: {
        postalCode: '1925',
        street: 'Calle',
        number: '123',
        city: 'Ensenada',
        province: 'Buenos Aires',
        country: 'Argentina',
        senderName: 'Tienda',
        senderEmail: 'tienda@example.com',
        senderPhone: '2210000000',
        senderDocumentType: 'CUIT',
        senderDocumentNumber: '30700000001',
      },
      defaultParcel: {
        weightGrams: 500,
        lengthCm: 20,
        widthCm: 15,
        heightCm: 10,
      },
      trackingSyncMinutes: 15,
      version: 1,
    });
    fixture.detectChanges();

    fixture.componentInstance.testPostalCode = '1900';
    expect(fixture.componentInstance.canTest()).toBe(true);
    fixture.componentInstance.testConnection();

    http.expectOne('/api/v1/auth/csrf').flush({});
    const request = http.expectOne(baseUrl + '/test');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.username).toBeNull();
    expect(request.request.body.password).toBeNull();
    expect(request.request.body.destinationPostalCode).toBe('1900');
    request.flush({
      success: true,
      authenticationOk: true,
      quoteOk: true,
      environment: 'SANDBOX',
      destinationPostalCode: '1900',
      serviceCode: 'ANDREANI_DOMICILIO',
      providerCost: '3450.00',
      checks: [
        { code: 'AUTH', success: true, message: 'Andreani aceptó las credenciales.' },
        { code: 'QUOTE', success: true, message: 'Andreani devolvió una tarifa.' },
      ],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Conexión lista para piloto');
    expect(fixture.nativeElement.textContent).toContain('Autenticación');
    expect(fixture.nativeElement.textContent).toContain('Cotización');
    expect(fixture.nativeElement.textContent).toContain('3450.00');
  });
});
