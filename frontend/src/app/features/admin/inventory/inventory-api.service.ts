import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AdjustmentResponse,
  InventoryAvailability,
  InventoryItem,
  InventoryPage,
  MovementPage,
  StockAdjustment,
} from './inventory.models';

@Injectable({ providedIn: 'root' })
export class InventoryApiService {
  private readonly http = inject(HttpClient);

  list(
    storeSlug: string,
    page: number,
    size: number,
    q: string,
    availability: InventoryAvailability,
  ): Observable<InventoryPage> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('availability', availability);
    if (q) params = params.set('q', q);
    return this.http.get<InventoryPage>(this.collectionUrl(storeSlug), { params });
  }

  get(storeSlug: string, variantId: string): Observable<InventoryItem> {
    return this.http.get<InventoryItem>(this.variantUrl(storeSlug, variantId));
  }

  movements(
    storeSlug: string,
    variantId: string,
    page: number,
    size: number,
  ): Observable<MovementPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<MovementPage>(
      `${this.variantUrl(storeSlug, variantId)}/movements`,
      { params },
    );
  }

  adjust(
    storeSlug: string,
    variantId: string,
    idempotencyKey: string,
    body: StockAdjustment,
  ): Observable<AdjustmentResponse> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<AdjustmentResponse>(
      `${this.variantUrl(storeSlug, variantId)}/adjustments`,
      body,
      { headers },
    );
  }

  private collectionUrl(storeSlug: string): string {
    return `/api/v1/stores/${encodeURIComponent(storeSlug)}/admin/inventory`;
  }

  private variantUrl(storeSlug: string, variantId: string): string {
    return `${this.collectionUrl(storeSlug)}/variants/${encodeURIComponent(variantId)}`;
  }
}
