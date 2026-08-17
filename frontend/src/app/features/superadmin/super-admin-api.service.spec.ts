import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { CsrfService } from '../../core/auth/csrf.service';
import { SuperAdminApiService } from './super-admin-api.service';

describe('SuperAdminApiService', () => {
  let service: SuperAdminApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: CsrfService,
          useValue: { ensureToken: () => of('csrf-token') },
        },
      ],
    });
    service = TestBed.inject(SuperAdminApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends server-side company filters without connection identifiers', () => {
    service.companies(1, 20, 'SUSPENDED', ' urban ').subscribe();

    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/superadmin/companies' &&
        candidate.params.get('page') === '1' &&
        candidate.params.get('status') === 'SUSPENDED' &&
        candidate.params.get('q') === 'urban',
    );
    expect(request.request.params.has('databaseKey')).toBe(false);
    request.flush({ items: [], page: 1, size: 20, totalItems: 0, totalPages: 0 });
  });

  it('obtains CSRF before suspending a company', () => {
    service.suspend('company-1').subscribe();

    const request = http.expectOne(
      '/api/v1/superadmin/companies/company-1/suspend',
    );
    expect(request.request.method).toBe('POST');
    request.flush({});
  });

  it('creates a company through the protected provisioning endpoint', () => {
    service
      .createCompany({
        name: 'Urban Clothes',
        slug: 'urban-clothes',
        industry: 'Indumentaria',
        administratorEmail: 'juan@example.com',
        administratorName: 'Juan Pérez',
        administratorPhone: null,
        domain: null,
        initialPassword: 'initial-password',
        status: 'ACTIVE',
      })
      .subscribe();

    const request = http.expectOne('/api/v1/superadmin/companies');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).not.toHaveProperty('databaseKey');
    expect(request.request.body).not.toHaveProperty('databaseName');
    request.flush({});
  });

  it('updates branding through a protected endpoint without exposing database identifiers', () => {
    service.updateBranding('company/1', {
      primaryColor: '#112233',
      secondaryColor: '#223344',
      backgroundColor: '#FFFFFF',
      textColor: '#101010',
      font: 'SANS',
      heroTitle: 'Nueva colección',
      heroSubtitle: null,
      template: 'MODERN',
    }).subscribe();

    const request = http.expectOne('/api/v1/superadmin/companies/company%2F1/branding');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).not.toHaveProperty('databaseKey');
    request.flush({});
  });

  it('uploads and deletes tenant branding assets through CSRF-protected endpoints', () => {
    const file = new File(['image'], 'logo.png', { type: 'image/png' });
    service.uploadBrandingAsset('company-1', 'logo', file).subscribe();

    const upload = http.expectOne(
      '/api/v1/superadmin/companies/company-1/branding/assets/logo',
    );
    expect(upload.request.method).toBe('PUT');
    expect(upload.request.body).toBeInstanceOf(FormData);
    upload.flush({});

    service.deleteBrandingAsset('company-1', 'logo').subscribe();
    const deletion = http.expectOne(
      '/api/v1/superadmin/companies/company-1/branding/assets/logo',
    );
    expect(deletion.request.method).toBe('DELETE');
    deletion.flush({});
  });
});
