import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { CsrfService } from '../../../core/auth/csrf.service';
import { StoreSettingsApiService } from './store-settings-api.service';

describe('StoreSettingsApiService', () => {
  let api: StoreSettingsApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CsrfService, useValue: { ensureToken: () => of(undefined) } },
      ],
    });
    api = TestBed.inject(StoreSettingsApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads and updates the settings of the selected tenant', () => {
    api.get('tienda/a').subscribe();
    http.expectOne('/api/v1/stores/tienda%2Fa/admin/settings').flush({});

    api.update('tienda/a', {
      storeName: 'Tienda', contactPhone: '1111111', contactEmail: '',
      pickupAddress: 'Calle 123', pickupInstructions: '',
      bankTransferEnabled: false, bankName: '', bankAccountHolder: '',
      bankAlias: '', bankCbuCvu: '',
    }).subscribe();
    const request = http.expectOne('/api/v1/stores/tienda%2Fa/admin/settings');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).not.toHaveProperty('brandTheme');
    request.flush({});
  });

  it('loads and saves tenant branding through the scoped endpoint', () => {
    api.getBranding('tienda/a').subscribe();
    const get = http.expectOne('/api/v1/stores/tienda%2Fa/admin/branding');
    expect(get.request.method).toBe('GET');
    get.flush({});

    api.updateBranding('tienda/a', {
      primaryColor: '#112233', secondaryColor: '#223344', backgroundColor: '#FFFFFF',
      textColor: '#111111', font: 'SANS', heroTitle: null, heroSubtitle: null,
      template: 'FRESH',
    }).subscribe();
    const put = http.expectOne('/api/v1/stores/tienda%2Fa/admin/branding');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body.template).toBe('FRESH');
    put.flush({});
  });
});
