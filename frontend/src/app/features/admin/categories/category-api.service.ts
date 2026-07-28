import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Category, CategoryStatusChange, SaveCategory } from './category.models';

@Injectable({ providedIn: 'root' })
export class CategoryApiService {
  private readonly http = inject(HttpClient);

  list(storeSlug: string): Observable<Category[]> {
    return this.http.get<Category[]>(this.collectionUrl(storeSlug));
  }

  get(storeSlug: string, categoryId: string): Observable<Category> {
    return this.http.get<Category>(this.itemUrl(storeSlug, categoryId));
  }

  create(storeSlug: string, category: SaveCategory): Observable<Category> {
    return this.http.post<Category>(this.collectionUrl(storeSlug), category);
  }

  update(
    storeSlug: string,
    categoryId: string,
    category: SaveCategory,
  ): Observable<Category> {
    return this.http.put<Category>(this.itemUrl(storeSlug, categoryId), category);
  }

  setActive(
    storeSlug: string,
    categoryId: string,
    active: boolean,
  ): Observable<Category> {
    const change: CategoryStatusChange = { active };
    return this.http.patch<Category>(
      `${this.itemUrl(storeSlug, categoryId)}/status`,
      change,
    );
  }

  private collectionUrl(storeSlug: string): string {
    return `/api/v1/stores/${encodeURIComponent(storeSlug)}/admin/categories`;
  }

  private itemUrl(storeSlug: string, categoryId: string): string {
    return `${this.collectionUrl(storeSlug)}/${encodeURIComponent(categoryId)}`;
  }
}
