import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { CheckoutProStart, PaymentReturnStatus } from './payment.models';

@Injectable({ providedIn: 'root' })
export class PaymentApiService {
  private readonly http = inject(HttpClient);

  startCheckout(
    storeSlug: string,
    orderId: string,
    lookupToken: string,
    idempotencyKey: string,
  ): Observable<CheckoutProStart> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    const params = new HttpParams().set('token', lookupToken);
    return this.http.post<CheckoutProStart>(
      `${this.ordersUrl(storeSlug)}/${encodeURIComponent(orderId)}/payments/checkout-pro`,
      {},
      { headers, params },
    );
  }

  getReturnStatus(storeSlug: string, returnToken: string): Observable<PaymentReturnStatus> {
    return this.http.get<PaymentReturnStatus>(
      `/api/v1/stores/${encodeURIComponent(storeSlug)}/payment-returns/${encodeURIComponent(returnToken)}`,
    );
  }

  reconcileReturn(
    storeSlug: string,
    returnToken: string,
    providerPaymentId: string,
  ): Observable<PaymentReturnStatus> {
    const params = new HttpParams().set('paymentId', providerPaymentId);
    return this.http.post<PaymentReturnStatus>(
      `/api/v1/stores/${encodeURIComponent(storeSlug)}/payment-returns/${encodeURIComponent(returnToken)}/reconcile`,
      {},
      { params },
    );
  }

  private ordersUrl(storeSlug: string): string {
    return `/api/v1/stores/${encodeURIComponent(storeSlug)}/orders`;
  }
}
