import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { SuperAdminApiService } from '../super-admin-api.service';
import { CompanyDetailPage } from './company-detail';

describe('CompanyDetailPage', () => {
  it('renders all sections and saves only editable company configuration', () => {
    const company = {
      id: 'company-1',
      name: 'Urban Clothes',
      slug: 'urban-clothes',
      status: 'ACTIVE' as const,
      primaryAdministrator: { name: 'Juan Pérez', email: 'juan@example.com' },
      createdAt: '2026-08-17T12:00:00Z',
      industry: 'Indumentaria',
      phone: null,
      domain: null,
      lastActivityAt: null,
    };
    const updateCompany = vi.fn().mockReturnValue(of({ ...company, domain: 'urban.example.com' }));
    const api = {
      company: vi.fn().mockReturnValue(of(company)),
      companyUsers: vi.fn().mockReturnValue(
        of([
          {
            id: 'user-1',
            name: 'Juan Pérez',
            email: 'juan@example.com',
            role: 'OWNER',
            membershipStatus: 'ACTIVE',
            userStatus: 'ACTIVE',
            joinedAt: '2026-08-17T12:00:00Z',
          },
        ]),
      ),
      branding: vi.fn().mockReturnValue(
        of({
          primaryColor: '#112233',
          secondaryColor: '#223344',
          backgroundColor: '#FFFFFF',
          textColor: '#111111',
          font: 'SANS',
          heroTitle: 'Nueva colección',
          heroSubtitle: null,
          template: 'MODERN',
          logoUrl: null,
          faviconUrl: null,
          heroImageUrl: null,
        }),
      ),
      companyActivity: vi.fn().mockReturnValue(
        of({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 }),
      ),
      companyInfrastructure: vi.fn().mockReturnValue(
        of({
          isolationMode: 'DATABASE_PER_TENANT',
          provisioningStatus: 'READY',
          provisionedAt: '2026-08-17T12:00:00Z',
          updatedAt: '2026-08-17T12:00:00Z',
          customDomainConfigured: false,
          lastActivityAt: null,
        }),
      ),
      updateCompany,
      activate: vi.fn(),
      suspend: vi.fn(),
      retryProvisioning: vi.fn(),
    };
    const params = of(convertToParamMap({ companyId: 'company-1' }));

    TestBed.configureTestingModule({
      imports: [CompanyDetailPage],
      providers: [
        provideRouter([]),
        { provide: SuperAdminApiService, useValue: api },
        {
          provide: ActivatedRoute,
          useValue: {
            pathFromRoot: [{ paramMap: params }],
            snapshot: { paramMap: convertToParamMap({ companyId: 'company-1' }), parent: null },
          },
        },
      ],
    });

    const fixture = TestBed.createComponent(CompanyDetailPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    expect(fixture.nativeElement.querySelectorAll('.tabs button').length).toBe(6);
    expect(fixture.nativeElement.textContent).toContain('Urban Clothes');

    component.selectTab('configuration');
    fixture.detectChanges();
    component.editForm.patchValue({ domain: 'urban.example.com' });
    component.saveCompany();

    expect(updateCompany).toHaveBeenCalledWith('company-1', {
      name: 'Urban Clothes',
      industry: 'Indumentaria',
      phone: null,
      domain: 'urban.example.com',
    });
    expect(updateCompany.mock.calls[0][1]).not.toHaveProperty('slug');
    expect(updateCompany.mock.calls[0][1]).not.toHaveProperty('databaseKey');
  });
});
