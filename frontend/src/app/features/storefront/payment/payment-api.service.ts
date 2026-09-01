import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  BankTransferPayment,
  CheckoutProStart,
  PaymentMethods,
  PaymentReturnStatus,
  QrOrderStart,
} from './payment.models';

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

  startQrOrder(
    storeSlug: string,
    orderId: string,
    lookupToken: string,
    idempotencyKey: string,
  ): Observable<QrOrderStart> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    const params = new HttpParams().set('token', lookupToken);
    return this.http.post<QrOrderStart>(
      `${this.ordersUrl(storeSlug)}/${encodeURIComponent(orderId)}/payments/qr`,
      {},
      { headers, params },
    );
  }

  getCurrentQrOrder(
    storeSlug: string,
    orderId: string,
    lookupToken: string,
  ): Observable<QrOrderStart | null> {
    const params = new HttpParams().set('token', lookupToken);
    return this.http.get<QrOrderStart | null>(
      `${this.ordersUrl(storeSlug)}/${encodeURIComponent(orderId)}/payments/qr`,
      { params },
    );
  }

  reconcilePendingCheckout(
    storeSlug: string,
    orderId: string,
    lookupToken: string,
  ): Observable<PaymentReturnStatus | null> {
    const params = new HttpParams().set('token', lookupToken);
    return this.http.post<PaymentReturnStatus | null>(
      `${this.ordersUrl(storeSlug)}/${encodeURIComponent(orderId)}/payments/checkout-pro/reconcile`,
      {},
      { params },
    );
  }

  getMethods(storeSlug: string): Observable<PaymentMethods> {
    return this.http.get<PaymentMethods>(
      `/api/v1/stores/${encodeURIComponent(storeSlug)}/payment-methods`,
    );
  }

  startBankTransfer(
    storeSlug: string,
    orderId: string,
    lookupToken: string,
  ): Observable<BankTransferPayment> {
    const params = new HttpParams().set('token', lookupToken);
    return this.http.post<BankTransferPayment>(
      `${this.ordersUrl(storeSlug)}/${encodeURIComponent(orderId)}/payments/bank-transfer`,
      {},
      { params },
    );
  }

  getCurrentBankTransfer(
    storeSlug: string,
    orderId: string,
    lookupToken: string,
  ): Observable<BankTransferPayment> {
    const params = new HttpParams().set('token', lookupToken);
    return this.http.get<BankTransferPayment>(
      `${this.ordersUrl(storeSlug)}/${encodeURIComponent(orderId)}/payments/bank-transfer`,
      { params },
    );
  }

  uploadBankTransferReceipt(
    storeSlug: string,
    orderId: string,
    paymentId: string,
    lookupToken: string,
    file: File,
  ): Observable<BankTransferPayment> {
    const params = new HttpParams().set('token', lookupToken);
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<BankTransferPayment>(
      `${this.ordersUrl(storeSlug)}/${encodeURIComponent(orderId)}/payments/bank-transfer/${encodeURIComponent(paymentId)}/receipt`,
      body,
      { params },
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
  ): Observable<PaymentReturnStatus> {
    return this.http.post<PaymentReturnStatus>(
      `/api/v1/stores/${encodeURIComponent(storeSlug)}/payment-returns/${encodeURIComponent(returnToken)}/reconcile`,
      {},
    );
  }

  inspectReturn(storeSlug: string, returnToken: string): Observable<PaymentReturnStatus> {
    return this.http.post<PaymentReturnStatus>(
      `/api/v1/stores/${encodeURIComponent(storeSlug)}/payment-returns/${encodeURIComponent(returnToken)}/inspect`,
      {},
    );
  }

  private ordersUrl(storeSlug: string): string {
    return `/api/v1/stores/${encodeURIComponent(storeSlug)}/orders`;
  }
}
