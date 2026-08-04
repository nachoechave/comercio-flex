import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CreateProduct,
  ProductCategory,
  ProductDetail,
  ProductPage,
  ProductImage,
  ProductQuery,
  ProductStatus,
  ProductVariant,
  SaveVariant,
  UpdateProduct,
  UpdateVariant,
} from './product.models';

@Injectable({ providedIn: 'root' })
export class ProductApiService {
  private readonly http = inject(HttpClient);

  list(storeSlug: string, query: ProductQuery): Observable<ProductPage> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.query) params = params.set('q', query.query);
    if (query.status) params = params.set('status', query.status);
    if (query.categoryId) params = params.set('categoryId', query.categoryId);
    return this.http.get<ProductPage>(this.collectionUrl(storeSlug), { params });
  }

  get(storeSlug: string, productId: string): Observable<ProductDetail> {
    return this.http.get<ProductDetail>(this.productUrl(storeSlug, productId));
  }

  create(storeSlug: string, body: CreateProduct): Observable<ProductDetail> {
    return this.http.post<ProductDetail>(this.collectionUrl(storeSlug), body);
  }

  update(storeSlug: string, productId: string, body: UpdateProduct): Observable<ProductDetail> {
    return this.http.put<ProductDetail>(this.productUrl(storeSlug, productId), body);
  }

  setStatus(
    storeSlug: string,
    productId: string,
    status: ProductStatus,
    version: number,
  ): Observable<ProductDetail> {
    return this.http.patch<ProductDetail>(
      `${this.productUrl(storeSlug, productId)}/status`,
      { status, version },
    );
  }

  uploadImage(
    storeSlug: string,
    productId: string,
    file: File,
    altText: string,
  ): Observable<ProductImage> {
    const body = new FormData();
    body.append('file', file);
    body.append('altText', altText);
    return this.http.put<ProductImage>(`${this.productUrl(storeSlug, productId)}/image`, body);
  }

  deleteImage(storeSlug: string, productId: string): Observable<void> {
    return this.http.delete<void>(`${this.productUrl(storeSlug, productId)}/image`);
  }

  createVariant(
    storeSlug: string,
    productId: string,
    body: SaveVariant,
  ): Observable<ProductVariant> {
    return this.http.post<ProductVariant>(
      this.variantCollectionUrl(storeSlug, productId),
      body,
    );
  }

  updateVariant(
    storeSlug: string,
    productId: string,
    variantId: string,
    body: UpdateVariant,
  ): Observable<ProductVariant> {
    return this.http.put<ProductVariant>(
      this.variantUrl(storeSlug, productId, variantId),
      body,
    );
  }

  setVariantActive(
    storeSlug: string,
    productId: string,
    variantId: string,
    active: boolean,
    version: number,
  ): Observable<ProductVariant> {
    return this.http.patch<ProductVariant>(
      `${this.variantUrl(storeSlug, productId, variantId)}/status`,
      { active, version },
    );
  }

  listCategories(storeSlug: string): Observable<ProductCategory[]> {
    return this.http.get<ProductCategory[]>(
      `/api/v1/stores/${encodeURIComponent(storeSlug)}/admin/categories`,
    );
  }

  private collectionUrl(storeSlug: string): string {
    return `/api/v1/stores/${encodeURIComponent(storeSlug)}/admin/products`;
  }

  private productUrl(storeSlug: string, productId: string): string {
    return `${this.collectionUrl(storeSlug)}/${encodeURIComponent(productId)}`;
  }

  private variantCollectionUrl(storeSlug: string, productId: string): string {
    return `${this.productUrl(storeSlug, productId)}/variants`;
  }

  private variantUrl(storeSlug: string, productId: string, variantId: string): string {
    return `${this.variantCollectionUrl(storeSlug, productId)}/${encodeURIComponent(variantId)}`;
  }
}
