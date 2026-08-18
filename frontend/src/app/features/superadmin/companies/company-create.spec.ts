import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';

import { SuperAdminApiService } from '../super-admin-api.service';
import { CompanyCreate } from './company-create';

describe('CompanyCreate', () => {
  it('validates and sends only business onboarding data', () => {
    const createCompany = vi.fn().mockReturnValue(
      of({ id: 'company-1', slug: 'urban-clothes' }),
    );
    TestBed.configureTestingModule({
      imports: [CompanyCreate],
      providers: [
        provideRouter([]),
        {
          provide: SuperAdminApiService,
          useValue: {
            createCompany,
            provisioningCapability: () =>
              of({ available: true, provider: 'MANAGED_MYSQL', reason: null }),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(CompanyCreate);
    const component = fixture.componentInstance;
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    component.form.setValue({
      name: 'Urban Clothes',
      slug: 'urban-clothes',
      industry: 'Indumentaria',
      administratorName: 'Juan Pérez',
      administratorEmail: 'juan@example.com',
      administratorPhone: '',
      domain: '',
      initialPassword: 'initial-password',
      status: 'ACTIVE',
    });
    component.submit();

    expect(createCompany).toHaveBeenCalledWith(
      expect.objectContaining({
        slug: 'urban-clothes',
        administratorPhone: null,
        domain: null,
      }),
    );
    expect(createCompany.mock.calls[0][0]).not.toHaveProperty('databaseKey');
    expect(router.navigate).toHaveBeenCalledWith(
      ['/superadmin/empresas', 'company-1'],
      expect.any(Object),
    );
  });

  it('disables creation and explains the missing provider configuration', () => {
    const createCompany = vi.fn();
    TestBed.configureTestingModule({
      imports: [CompanyCreate],
      providers: [
        provideRouter([]),
        {
          provide: SuperAdminApiService,
          useValue: {
            createCompany,
            provisioningCapability: () =>
              of({
                available: false,
                provider: 'MANAGED_MYSQL',
                reason: 'Activá TENANT_PROVISIONING_ENABLED.',
              }),
          },
        },
      ],
    });

    const fixture = TestBed.createComponent(CompanyCreate);
    fixture.detectChanges();

    expect(fixture.componentInstance.creationDisabled()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain(
      'Activá TENANT_PROVISIONING_ENABLED.',
    );
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    expect(createCompany).not.toHaveBeenCalled();
  });
});
