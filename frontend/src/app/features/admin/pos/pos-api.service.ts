import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { PosCatalogItem, PosSale, PosSalePayload } from './pos.models';

@Injectable({ providedIn: 'root' })
export class PosApiService {
  private readonly http = inject(HttpClient);

  catalog(storeSlug: string, branchId: string, q: string): Observable<PosCatalogItem[]> {
    let params = new HttpParams().set('branchId', branchId);
    if (q.trim()) params = params.set('q', q.trim());
    return this.http.get<PosCatalogItem[]>(`${this.baseUrl(storeSlug)}/catalog`, { params });
  }

  sales(storeSlug: string): Observable<PosSale[]> {
    return this.http.get<PosSale[]>(`${this.baseUrl(storeSlug)}/sales`);
  }

  createSale(
    storeSlug: string,
    idempotencyKey: string,
    payload: PosSalePayload,
  ): Observable<PosSale> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<PosSale>(`${this.baseUrl(storeSlug)}/sales`, payload, { headers });
  }

  private baseUrl(storeSlug: string): string {
    return `/api/v1/stores/${encodeURIComponent(storeSlug)}/admin/pos`;
  }
}
