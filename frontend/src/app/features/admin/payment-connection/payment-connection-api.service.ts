import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  PaymentAuthorizationStart,
  PaymentConnection,
  PaymentWebhookEventSummary,
  PaymentWebhookRetryResult,
} from './payment-connection.models';

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

  getFailedWebhooks(storeSlug: string): Observable<PaymentWebhookEventSummary[]> {
    return this.http.get<PaymentWebhookEventSummary[]>(
      `${this.webhooksUrl(storeSlug)}?status=DEAD`,
    );
  }

  retryWebhook(storeSlug: string, eventId: string): Observable<PaymentWebhookRetryResult> {
    return this.http.post<PaymentWebhookRetryResult>(
      `${this.webhooksUrl(storeSlug)}/${encodeURIComponent(eventId)}/retry`,
      {},
    );
  }

  private connectionUrl(storeSlug: string): string {
    return `/api/v1/stores/${encodeURIComponent(storeSlug)}/admin/payment-connection`;
  }

  private webhooksUrl(storeSlug: string): string {
    return `/api/v1/stores/${encodeURIComponent(storeSlug)}/admin/payment-webhooks`;
  }
}
