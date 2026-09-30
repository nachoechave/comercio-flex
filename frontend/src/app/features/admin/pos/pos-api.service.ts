import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { CashSession, PosCatalogItem, PosSale, PosSalePayload } from './pos.models';

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

  currentCashSession(storeSlug: string, branchId: string): Observable<CashSession | null> {
    const params = new HttpParams().set('branchId', branchId);
    return this.http.get<CashSession | null>(`${this.baseUrl(storeSlug)}/cash-sessions/current`, {
      params,
    });
  }

  cashSessions(storeSlug: string, branchId: string | null, limit = 30): Observable<CashSession[]> {
    let params = new HttpParams().set('limit', limit);
    if (branchId) params = params.set('branchId', branchId);
    return this.http.get<CashSession[]>(`${this.baseUrl(storeSlug)}/cash-sessions`, { params });
  }

  openCashSession(storeSlug: string, branchId: string, openingAmount: string): Observable<CashSession> {
    return this.http.post<CashSession>(`${this.baseUrl(storeSlug)}/cash-sessions/open`, {
      branchId,
      openingAmount,
    });
  }

  closeCashSession(storeSlug: string, sessionId: string, closingAmount: string): Observable<CashSession> {
    return this.http.post<CashSession>(
      `${this.baseUrl(storeSlug)}/cash-sessions/${encodeURIComponent(sessionId)}/close`,
      { closingAmount },
    );
  }

  private baseUrl(storeSlug: string): string {
    return `/api/v1/stores/${encodeURIComponent(storeSlug)}/admin/pos`;
  }
}
