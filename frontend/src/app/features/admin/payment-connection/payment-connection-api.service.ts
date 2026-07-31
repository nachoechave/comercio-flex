import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { PaymentAuthorizationStart, PaymentConnection } from './payment-connection.models';

@Injectable({ providedIn: 'root' })
export class PaymentConnectionApiService {
  private readonly http = inject(HttpClient);

  get(storeSlug: string): Observable<PaymentConnection> {
    return this.http.get<PaymentConnection>(this.connectionUrl(storeSlug));
  }

  startAuthorization(storeSlug: string): Observable<PaymentAuthorizationStart> {
    return this.http.post<PaymentAuthorizationStart>(
      `${this.connectionUrl(storeSlug)}/authorization`,
      {},
    );
  }

  disconnect(storeSlug: string): Observable<void> {
    return this.http.delete<void>(this.connectionUrl(storeSlug));
  }

  private connectionUrl(storeSlug: string): string {
    return `/api/v1/stores/${encodeURIComponent(storeSlug)}/admin/payment-connection`;
  }
}
