import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthenticatedSession } from './auth.models';
import { AuthService } from './auth.service';

const AUTHENTICATED_SESSION: AuthenticatedSession = {
  authenticated: true,
  user: {
    id: 'user-1',
    email: 'owner@example.com',
    displayName: 'Dueña Demo',
  },
  memberships: [
    {
      storeSlug: 'tienda-a',
      storeName: 'Tienda A',
      role: 'OWNER',
    },
  ],
};

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads and exposes the current session', () => {
    service.loadSession().subscribe();

    const request = http.expectOne('/api/v1/auth/session');
    expect(request.request.method).toBe('GET');
    request.flush(AUTHENTICATED_SESSION);

    expect(service.isAuthenticated()).toBe(true);
    expect(service.user()?.email).toBe('owner@example.com');
    expect(service.membershipFor('tienda-a')?.role).toBe('OWNER');
  });

  it('reuses a session already loaded', () => {
    service.loadSession().subscribe();
    http.expectOne('/api/v1/auth/session').flush({ authenticated: false });

    service.loadSession().subscribe((session) => expect(session.authenticated).toBe(false));
    http.expectNone('/api/v1/auth/session');
  });

  it('obtains a CSRF cookie before login and stores the returned session', () => {
    service.login({ email: 'owner@example.com', password: 'correct horse' }).subscribe();

    const csrf = http.expectOne('/api/v1/auth/csrf');
    expect(csrf.request.method).toBe('GET');
    csrf.flush({});

    const login = http.expectOne('/api/v1/auth/login');
    expect(login.request.method).toBe('POST');
    expect(login.request.body).toEqual({
      email: 'owner@example.com',
      password: 'correct horse',
    });
    login.flush(AUTHENTICATED_SESSION);
    expect(service.isAuthenticated()).toBe(true);
  });

  it('marks the client session anonymous after logout succeeds', () => {
    service.loadSession().subscribe();
    http.expectOne('/api/v1/auth/session').flush(AUTHENTICATED_SESSION);

    service.logout().subscribe();
    http.expectOne('/api/v1/auth/csrf').flush({});
    const logout = http.expectOne('/api/v1/auth/logout');
    expect(logout.request.method).toBe('POST');
    logout.flush(null);

    expect(service.isAuthenticated()).toBe(false);
  });
});
