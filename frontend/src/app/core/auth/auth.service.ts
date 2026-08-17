import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, finalize, map, Observable, of, shareReplay, switchMap, tap } from 'rxjs';

import {
  AnonymousSession,
  AuthenticatedSession,
  CurrentSession,
  LoginCredentials,
  MembershipSummary,
} from './auth.models';
import { CsrfService } from './csrf.service';

const ANONYMOUS_SESSION: AnonymousSession = { authenticated: false };

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);
  private readonly sessionState = signal<CurrentSession | null>(null);
  private sessionRequest?: Observable<CurrentSession>;

  readonly session = this.sessionState.asReadonly();
  readonly isResolved = computed(() => this.sessionState() !== null);
  readonly isAuthenticated = computed(() => this.sessionState()?.authenticated === true);
  readonly user = computed(() => {
    const session = this.sessionState();
    return session?.authenticated ? session.user : null;
  });
  readonly isSuperAdmin = computed(
    () => this.user()?.platformRole === 'SUPER_ADMIN',
  );
  readonly memberships = computed<MembershipSummary[]>(() => {
    const session = this.sessionState();
    return session?.authenticated ? session.memberships : [];
  });

  loadSession(force = false): Observable<CurrentSession> {
    const current = this.sessionState();
    if (!force && current !== null) {
      return of(current);
    }

    if (!force && this.sessionRequest) {
      return this.sessionRequest;
    }

    const request = this.http.get<CurrentSession>('/api/v1/auth/session').pipe(
      catchError(() => of(ANONYMOUS_SESSION)),
      tap((session) => this.sessionState.set(session)),
      finalize(() => {
        if (this.sessionRequest === request) {
          this.sessionRequest = undefined;
        }
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );

    this.sessionRequest = request;
    return request;
  }

  login(credentials: LoginCredentials): Observable<AuthenticatedSession> {
    return this.csrf.ensureToken().pipe(
      switchMap(() =>
        this.http.post<CurrentSession>('/api/v1/auth/login', credentials),
      ),
      tap((session) => this.sessionState.set(session)),
      map((session) => {
        if (!session.authenticated) {
          throw new Error('The server did not create an authenticated session.');
        }
        return session;
      }),
    );
  }

  logout(): Observable<void> {
    return this.csrf.ensureToken().pipe(
      switchMap(() => this.http.post<void>('/api/v1/auth/logout', {})),
      tap(() => {
        this.markAnonymous();
        this.csrf.clearToken();
      }),
    );
  }

  markAnonymous(): void {
    this.sessionState.set(ANONYMOUS_SESSION);
    this.sessionRequest = undefined;
  }

  membershipFor(storeSlug: string): MembershipSummary | undefined {
    return this.memberships().find((membership) => membership.storeSlug === storeSlug);
  }
}
