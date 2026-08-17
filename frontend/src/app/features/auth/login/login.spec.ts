import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let component: Login;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('shows accessible validation and does not submit an invalid form', () => {
    component.submit();
    fixture.detectChanges();

    const email = fixture.nativeElement.querySelector('#email') as HTMLInputElement;
    expect(email.getAttribute('aria-invalid')).toBe('true');
    expect(fixture.nativeElement.textContent).toContain('Ingresá tu correo electrónico.');
    http.expectNone('/api/v1/auth/login');
  });

  it('logs in and navigates directly when there is one membership', async () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    component.form.setValue({
      email: 'owner@example.com',
      password: 'correct horse',
    });

    component.submit();
    http.expectOne('/api/v1/auth/csrf').flush({});
    http.expectOne('/api/v1/auth/login').flush({
      authenticated: true,
      user: {
        id: 'user-1',
        email: 'owner@example.com',
        displayName: 'Dueña Demo',
        platformRole: 'USER',
      },
      memberships: [
        {
          storeSlug: 'tienda-a',
          storeName: 'Tienda A',
          role: 'OWNER',
        },
      ],
    });

    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(['/tiendas', 'tienda-a', 'admin']);
  });
});
