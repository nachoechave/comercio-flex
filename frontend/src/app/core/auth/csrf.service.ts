import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class CsrfService {
  private readonly http = inject(HttpClient);
  private tokenRequest?: Observable<void>;

  ensureToken(): Observable<void> {
    this.tokenRequest ??= this.http
      .get<void>('/api/v1/auth/csrf')
      .pipe(shareReplay({ bufferSize: 1, refCount: false }));

    return this.tokenRequest;
  }

  clearToken(): void {
    this.tokenRequest = undefined;
  }
}
