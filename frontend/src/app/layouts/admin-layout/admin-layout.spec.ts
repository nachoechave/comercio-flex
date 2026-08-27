import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
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
            user: () => ({ id: 'user-1', displayName: 'Ana Admin', email: 'ana@example.com' }),
            memberships: () => [{ storeSlug: 'tienda-a', storeName: 'Tienda A', role }],
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

  it('shows commerce settings to owner and admin but hides them with home from staff', () => {
    role = 'STAFF';
    render();
    const labels = [...fixture.nativeElement.querySelectorAll('nav a')].map(
      (candidate: HTMLAnchorElement) => candidate.textContent?.trim(),
    );
    expect(labels).not.toContain('Inicio');
    expect(labels).not.toContain('Comercio');
    expect(labels).toContain('Pedidos');
  });

  it('opens and closes the responsive navigation with keyboard support', () => {
    render();
    const menu = fixture.nativeElement.querySelector('.menu-button') as HTMLButtonElement;
    menu.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.mobileNavigationOpen()).toBe(true);
    expect(menu.getAttribute('aria-expanded')).toBe('true');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(fixture.componentInstance.mobileNavigationOpen()).toBe(false);
  });

  it('shows the real store and authenticated user context', () => {
    render();
    const text = fixture.nativeElement.textContent.replace(/\s+/g, ' ');
    expect(text).toContain('Tienda A');
    expect(text).toContain('Ana Admin');
    expect(text).toContain('Ver tienda');
  });

  it('highlights only the navigation item for the current operational route', () => {
    render();
    fixture.componentInstance.currentUrl.set(
      '/tiendas/tienda-a/admin/pedidos/transferencias',
    );
    fixture.detectChanges();

    const orders = fixture.nativeElement.querySelector('a[href$="/pedidos"]');
    const transfers = fixture.nativeElement.querySelector('a[href$="/pedidos/transferencias"]');
    expect(orders.classList.contains('active')).toBe(false);
    expect(transfers.classList.contains('active')).toBe(true);
  });

  it('logs out and returns to the login route', () => {
    render();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    (fixture.nativeElement.querySelector('.logout-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith(['/admin/login']);
  });
});
