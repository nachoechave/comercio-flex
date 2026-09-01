import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  PaymentAuthorizationStart,
  PaymentConnection,
  PaymentStoreSettings,
  PaymentWebhookEventSummary,
  PaymentWebhookRetryResult,
  ConfigureQrRequest,
  QrSetup,
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

  getQrSetup(storeSlug: string): Observable<QrSetup> {
    return this.http.get<QrSetup>(`${this.connectionUrl(storeSlug)}/qr`);
  }

  discoverQrSetup(storeSlug: string): Observable<QrSetup> {
    return this.http.post<QrSetup>(`${this.connectionUrl(storeSlug)}/qr/discovery`, {});
  }

  configureQr(storeSlug: string, request: ConfigureQrRequest): Observable<QrSetup> {
    return this.http.post<QrSetup>(
      `${this.connectionUrl(storeSlug)}/qr/configuration`,
      request,
    );
  }

  getFailedWebhooks(storeSlug: string): Observable<PaymentWebhookEventSummary[]> {
    return this.http.get<PaymentWebhookEventSummary[]>(
      `${this.webhooksUrl(storeSlug)}?status=DEAD`,
    );
  }

  getStoreSettings(storeSlug: string): Observable<PaymentStoreSettings> {
    return this.http.get<PaymentStoreSettings>(
      `/api/v1/stores/${encodeURIComponent(storeSlug)}/settings`,
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
