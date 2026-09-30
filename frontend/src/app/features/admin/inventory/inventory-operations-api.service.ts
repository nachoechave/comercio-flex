import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  InventoryTransfer,
  InventoryTransferPayload,
  LowStockSummary,
  OperationalInventoryMovement,
} from './inventory-operations.models';

@Injectable({ providedIn: 'root' })
export class InventoryOperationsApiService {
  private readonly http = inject(HttpClient);

  movements(
    storeSlug: string,
    branchId: string | null,
    limit = 100,
  ): Observable<OperationalInventoryMovement[]> {
    let params = new HttpParams().set('limit', limit);
    if (branchId) params = params.set('branchId', branchId);
    return this.http.get<OperationalInventoryMovement[]>(`${this.baseUrl(storeSlug)}/movements`, {
      params,
    });
  }

  alerts(storeSlug: string, branchId: string | null, limit = 100): Observable<LowStockSummary> {
    let params = new HttpParams().set('limit', limit);
    if (branchId) params = params.set('branchId', branchId);
    return this.http.get<LowStockSummary>(`${this.baseUrl(storeSlug)}/alerts`, { params });
  }

  transfers(storeSlug: string, limit = 50): Observable<InventoryTransfer[]> {
    const params = new HttpParams().set('limit', limit);
    return this.http.get<InventoryTransfer[]>(`${this.baseUrl(storeSlug)}/transfers`, { params });
  }

  createTransfer(
    storeSlug: string,
    idempotencyKey: string,
    payload: InventoryTransferPayload,
  ): Observable<InventoryTransfer> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<InventoryTransfer>(`${this.baseUrl(storeSlug)}/transfers`, payload, {
      headers,
    });
  }

  private baseUrl(storeSlug: string): string {
    return `/api/v1/stores/${encodeURIComponent(storeSlug)}/admin/inventory/operations`;
  }
}
