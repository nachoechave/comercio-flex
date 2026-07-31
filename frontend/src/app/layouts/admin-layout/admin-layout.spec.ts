import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AdminRole } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { AdminLayout } from './admin-layout';

describe('AdminLayout payment navigation', () => {
  let fixture: ComponentFixture<AdminLayout>;
  let role: AdminRole;

  beforeEach(async () => {
    role = 'OWNER';
    await TestBed.configureTestingModule({
      imports: [AdminLayout],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({ storeSlug: 'tienda-a' })),
            snapshot: { paramMap: convertToParamMap({ storeSlug: 'tienda-a' }) },
          },
        },
        {
          provide: AuthService,
          useValue: {
            membershipFor: () => ({
              storeSlug: 'tienda-a',
              storeName: 'Tienda A',
              role,
            }),
            logout: () => of(undefined),
          },
        },
      ],
    }).compileComponents();
  });

  function render(): void {
    fixture = TestBed.createComponent(AdminLayout);
    fixture.detectChanges();
  }

  it('shows the payment settings link to an owner', () => {
    render();
    const link = [...fixture.nativeElement.querySelectorAll('nav a')].find(
      (candidate: HTMLAnchorElement) => candidate.textContent?.trim() === 'Pagos',
    ) as HTMLAnchorElement | undefined;

    expect(link).toBeTruthy();
    expect(link?.getAttribute('href')).toBe('/tiendas/tienda-a/admin/configuracion/pagos');
  });

  it.each<AdminRole>(['ADMIN', 'STAFF'])(
    'hides the payment settings link from %s',
    (restrictedRole) => {
      role = restrictedRole;
      render();

      const labels = [...fixture.nativeElement.querySelectorAll('nav a')].map(
        (candidate: HTMLAnchorElement) => candidate.textContent?.trim(),
      );
      expect(labels).not.toContain('Pagos');
    },
  );
});
