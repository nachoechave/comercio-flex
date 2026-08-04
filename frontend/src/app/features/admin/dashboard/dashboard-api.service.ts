import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { DashboardSummary } from './dashboard.models';

@Injectable({ providedIn: 'root' })
export class DashboardApiService {
  private readonly http = inject(HttpClient);

  get(storeSlug: string): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(this.url(storeSlug));
  }

  updateThreshold(storeSlug: string, lowStockThreshold: string): Observable<DashboardSummary> {
    return this.http.put<DashboardSummary>(`${this.url(storeSlug)}/settings`, {
      lowStockThreshold,
    });
  }

  private url(storeSlug: string): string {
    return `/api/v1/stores/${encodeURIComponent(storeSlug)}/admin/dashboard`;
  }
}
