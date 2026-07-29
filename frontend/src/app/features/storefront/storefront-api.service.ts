import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  PublicCategory,
  PublicProductDetail,
  PublicProductPage,
  PublicProductQuery,
  StoreSettings,
} from './storefront.models';

@Injectable()
export class StorefrontApiService {
  private readonly http = inject(HttpClient);

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
}
