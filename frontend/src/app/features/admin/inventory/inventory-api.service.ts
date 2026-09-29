import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AdjustmentResponse,
  BranchAdjustmentResponse,
  BranchPayload,
  BranchStock,
  InventoryAvailability,
  InventoryItem,
  InventoryPage,
  MovementPage,
  StockAdjustment,
  StoreBranch,
} from './inventory.models';

@Injectable({ providedIn: 'root' })
export class InventoryApiService {
  private readonly http = inject(HttpClient);

  list(storeSlug: string, page: number, size: number, q: string, availability: InventoryAvailability): Observable<InventoryPage> {
    let params = new HttpParams().set('page', page).set('size', size).set('availability', availability);
    if (q) params = params.set('q', q);
    return this.http.get<InventoryPage>(this.collectionUrl(storeSlug), { params });
  }

  get(storeSlug: string, variantId: string): Observable<InventoryItem> {
    return this.http.get<InventoryItem>(this.variantUrl(storeSlug, variantId));
  }

  branches(storeSlug: string): Observable<StoreBranch[]> {
    return this.http.get<StoreBranch[]>(`${this.adminUrl(storeSlug)}/branches`);
  }

  createBranch(storeSlug: string, body: BranchPayload): Observable<StoreBranch> {
    return this.http.post<StoreBranch>(`${this.adminUrl(storeSlug)}/branches`, body);
  }

  updateBranch(storeSlug: string, branchId: string, body: BranchPayload): Observable<StoreBranch> {
    return this.http.put<StoreBranch>(`${this.adminUrl(storeSlug)}/branches/${encodeURIComponent(branchId)}`, body);
  }

  branchStock(storeSlug: string, variantId: string): Observable<BranchStock[]> {
    return this.http.get<BranchStock[]>(`${this.variantUrl(storeSlug, variantId)}/branches`);
  }

  movements(storeSlug: string, variantId: string, page: number, size: number): Observable<MovementPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<MovementPage>(`${this.variantUrl(storeSlug, variantId)}/movements`, { params });
  }

  adjust(storeSlug: string, variantId: string, idempotencyKey: string, body: StockAdjustment): Observable<AdjustmentResponse> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<AdjustmentResponse>(`${this.variantUrl(storeSlug, variantId)}/adjustments`, body, { headers });
  }

  adjustBranch(storeSlug: string, variantId: string, branchId: string, idempotencyKey: string, body: StockAdjustment): Observable<BranchAdjustmentResponse> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<BranchAdjustmentResponse>(
      `${this.variantUrl(storeSlug, variantId)}/branches/${encodeURIComponent(branchId)}/adjustments`,
      body,
      { headers },
    );
  }

  private adminUrl(storeSlug: string): string {
    return `/api/v1/stores/${encodeURIComponent(storeSlug)}/admin`;
  }

  private collectionUrl(storeSlug: string): string {
    return `${this.adminUrl(storeSlug)}/inventory`;
  }

  private variantUrl(storeSlug: string, variantId: string): string {
    return `${this.collectionUrl(storeSlug)}/variants/${encodeURIComponent(variantId)}`;
  }
}
