import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { authErrorInterceptor } from './auth-error.interceptor';
import { AuthService } from './auth.service';

describe('authErrorInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authErrorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => http.verify());

  it('invalidates the client session when the server returns 401', () => {
    auth.loadSession().subscribe();
    http.expectOne('/api/v1/auth/session').flush({
      authenticated: true,
      user: {
        id: 'user-1',
        email: 'owner@example.com',
        displayName: 'Dueña Demo',
        platformRole: 'USER',
      },
      memberships: [],
    });

    client.get('/api/v1/protected').subscribe({ error: () => undefined });
    http.expectOne('/api/v1/protected').flush(
      { title: 'No autenticado' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(auth.isAuthenticated()).toBe(false);
  });
});
