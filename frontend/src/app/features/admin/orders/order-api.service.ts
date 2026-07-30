import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { AdminOrderDetail, AdminOrderPage, OrderStatus } from './order.models';

@Injectable({ providedIn: 'root' })
export class OrderApiService {
  private readonly http = inject(HttpClient);

  list(
    storeSlug: string,
    page: number,
    size: number,
    query: string,
    status: OrderStatus | '',
  ): Observable<AdminOrderPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (query) params = params.set('q', query);
    if (status) params = params.set('status', status);
    return this.http.get<AdminOrderPage>(this.collectionUrl(storeSlug), { params });
  }

  get(storeSlug: string, orderId: string): Observable<AdminOrderDetail> {
    return this.http.get<AdminOrderDetail>(
      `${this.collectionUrl(storeSlug)}/${encodeURIComponent(orderId)}`,
    );
  }

  transition(
    storeSlug: string,
    orderId: string,
    idempotencyKey: string,
    targetStatus: OrderStatus,
    note: string,
  ): Observable<AdminOrderDetail> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<AdminOrderDetail>(
      `${this.collectionUrl(storeSlug)}/${encodeURIComponent(orderId)}/transitions`,
      { targetStatus, ...(note ? { note } : {}) },
      { headers },
    );
  }

  private collectionUrl(storeSlug: string): string {
    return `/api/v1/stores/${encodeURIComponent(storeSlug)}/admin/orders`;
  }
}
