import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, Observable, of, shareReplay } from 'rxjs';

interface StorefrontLinkResponse {
  url: string;
  customDomain: boolean;
}

@Injectable({ providedIn: 'root' })
export class AdminStorefrontLinkService {
  private readonly http = inject(HttpClient);
  private readonly cache = new Map<string, Observable<StorefrontLinkResponse>>();

  get(storeSlug: string): Observable<StorefrontLinkResponse> {
    const cached = this.cache.get(storeSlug);
    if (cached) return cached;

    const request = this.http
      .get<StorefrontLinkResponse>(
        `/api/v1/stores/${encodeURIComponent(storeSlug)}/admin/storefront-link`,
      )
      .pipe(
        catchError(() =>
          of({
            url: `/tiendas/${storeSlug}`,
            customDomain: false,
          }),
        ),
        shareReplay({ bufferSize: 1, refCount: false }),
      );

    this.cache.set(storeSlug, request);
    return request;
  }
}
