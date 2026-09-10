import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { RadioSiteApiService } from './radio-site-api.service';
import { CsrfService } from '../../core/auth/csrf.service';
import { of } from 'rxjs';

describe('RADIO site configuration API', () => {
  let api: RadioSiteApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [RadioSiteApiService, provideHttpClient(), provideHttpClientTesting(), { provide: CsrfService, useValue: { ensureToken: () => of('csrf') } }] });
    api = TestBed.inject(RadioSiteApiService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('loads public content scoped to the tenant', () => { api.get('radio/a').subscribe(); const request = http.expectOne('/api/v1/stores/radio%2Fa/radio-site'); expect(request.request.method).toBe('GET'); request.flush({ settings: {}, programs: [], team: [], sponsors: [] }); });
  it('writes settings through the tenant admin endpoint', () => { api.settings('radio-a', { heroTitle: 'Voz', heroSubtitle: null, description: null, youtubeUrl: null, instagramUrl: null, xUrl: null, whatsappUrl: null }).subscribe(); const request = http.expectOne('/api/v1/stores/radio-a/admin/radio-site'); expect(request.request.method).toBe('PUT'); request.flush({}); });
  it('keeps content deletes tenant scoped', () => { api.remove('radio-a', 'sponsors', 'id-1').subscribe(); const request = http.expectOne('/api/v1/stores/radio-a/admin/radio-site/sponsors/id-1'); expect(request.request.method).toBe('DELETE'); request.flush({}); });
});
