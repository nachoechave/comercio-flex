import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { switchMap } from 'rxjs';
import { CsrfService } from '../../core/auth/csrf.service';

export interface RadioProfile { firstName: string; lastName: string; email: string; phone: string | null; }
export type ProfileUpdate = Pick<RadioProfile, 'firstName' | 'lastName' | 'phone'>;
export interface Registration extends ProfileUpdate { email: string; password: string; }

@Injectable({ providedIn: 'root' })
export class RadioAccountApiService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);
  private base(slug: string) { return `/api/v1/stores/${encodeURIComponent(slug)}`; }

  register(slug: string, body: Registration) {
    return this.csrf.ensureToken().pipe(switchMap(() => this.http.post<{ message: string }>(`${this.base(slug)}/member-registration`, body)));
  }
  profile(slug: string) { return this.http.get<RadioProfile>(`${this.base(slug)}/me/profile`); }
  update(slug: string, body: ProfileUpdate) {
    return this.csrf.ensureToken().pipe(switchMap(() => this.http.put<RadioProfile>(`${this.base(slug)}/me/profile`, body)));
  }
  forgot(slug: string, email: string) {
    return this.csrf.ensureToken().pipe(switchMap(() => this.http.post<{ message: string }>(`${this.base(slug)}/account/password/forgot`, { email })));
  }
  reset(slug: string, token: string, password: string) {
    return this.csrf.ensureToken().pipe(switchMap(() => this.http.post<void>(`${this.base(slug)}/account/password/reset`, { token, password })));
  }
}
