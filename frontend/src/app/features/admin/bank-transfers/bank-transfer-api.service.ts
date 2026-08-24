import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { AdminBankTransferPayment } from './bank-transfer.models';

@Injectable({ providedIn: 'root' })
export class BankTransferApiService {
  private readonly http = inject(HttpClient);

  listPending(storeSlug: string): Observable<AdminBankTransferPayment[]> {
    return this.http.get<AdminBankTransferPayment[]>(this.collectionUrl(storeSlug));
  }

  get(storeSlug: string, paymentId: string): Observable<AdminBankTransferPayment> {
    return this.http.get<AdminBankTransferPayment>(
      `${this.collectionUrl(storeSlug)}/${encodeURIComponent(paymentId)}`,
    );
  }

  approve(storeSlug: string, paymentId: string): Observable<AdminBankTransferPayment> {
    return this.http.post<AdminBankTransferPayment>(
      `${this.collectionUrl(storeSlug)}/${encodeURIComponent(paymentId)}/approve`, {},
    );
  }

  reject(storeSlug: string, paymentId: string, reason: string): Observable<AdminBankTransferPayment> {
    return this.http.post<AdminBankTransferPayment>(
      `${this.collectionUrl(storeSlug)}/${encodeURIComponent(paymentId)}/reject`, { reason },
    );
  }

  receiptUrl(storeSlug: string, paymentId: string): string {
    return `${this.collectionUrl(storeSlug)}/${encodeURIComponent(paymentId)}/receipt`;
  }

  private collectionUrl(storeSlug: string): string {
    return `/api/v1/stores/${encodeURIComponent(storeSlug)}/admin/bank-transfer-payments`;
  }
}
