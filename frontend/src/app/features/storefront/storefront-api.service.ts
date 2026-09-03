import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

import {
  CreateGuestOrder,
  CreatedGuestOrder,
  GuestOrder,
  PublicCategory,
  PublicProductDetail,
  PublicProductPage,
  PublicProductQuery,
  StorefrontTenantResolution,
  StoreSettings,
} from './storefront.models';

@Injectable({
  providedIn: 'root',
})
export class StorefrontApiService {
  private readonly http = inject(HttpClient);

  private readonly storefrontResolution$ = this.http
    .get<StorefrontTenantResolution>('/api/v1/storefront/resolve')
    .pipe(shareReplay(1));

  resolveStorefront(): Observable<StorefrontTenantResolution> {
    return this.storefrontResolution$;
  }

  getSettings(storeSlug: string): Observable<StoreSettings> {
    return this.http.get<StoreSettings>(
      `/api/v1/stores/${encodeURIComponent(storeSlug)}/settings`,
    );
  }

  listCategories(storeSlug: string): Observable<PublicCategory[]> {
    return this.http.get<PublicCategory[]>(
      `/api/v1/stores/${encodeURIComponent(storeSlug)}/catalog/categories`,
    );
  }

  listProducts(storeSlug: string, query: PublicProductQuery): Observable<PublicProductPage> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.q) params = params.set('q', query.q);
    if (query.category) params = params.set('category', query.category);

    return this.http.get<PublicProductPage>(
      `/api/v1/stores/${encodeURIComponent(storeSlug)}/catalog/products`,
      { params },
    );
  }

  getProduct(storeSlug: string, productSlug: string): Observable<PublicProductDetail> {
    return this.http.get<PublicProductDetail>(
      `/api/v1/stores/${encodeURIComponent(storeSlug)}/catalog/products/${encodeURIComponent(productSlug)}`,
    );
  }

  createOrder(
    storeSlug: string,
    idempotencyKey: string,
    body: CreateGuestOrder,
  ): Observable<CreatedGuestOrder> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<CreatedGuestOrder>(this.ordersUrl(storeSlug), body, { headers });
  }

  getOrder(storeSlug: string, orderId: string, token: string): Observable<GuestOrder> {
    const params = new HttpParams().set('token', token);
    return this.http.get<GuestOrder>(
      `${this.ordersUrl(storeSlug)}/${encodeURIComponent(orderId)}`,
      { params },
    );
  }

  private ordersUrl(storeSlug: string): string {
    return `/api/v1/stores/${encodeURIComponent(storeSlug)}/orders`;
  }
}
