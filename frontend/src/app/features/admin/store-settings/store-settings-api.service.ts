import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';

import { CsrfService } from '../../../core/auth/csrf.service';
import { TenantBranding } from '../../storefront/storefront.models';
import { AdminStoreSettings, UpdateStoreSettings } from './store-settings.models';

@Injectable({ providedIn: 'root' })
export class StoreSettingsApiService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);

  get(storeSlug: string): Observable<AdminStoreSettings> {
    return this.http.get<AdminStoreSettings>(this.url(storeSlug));
  }

  update(storeSlug: string, body: UpdateStoreSettings): Observable<AdminStoreSettings> {
    return this.http.put<AdminStoreSettings>(this.url(storeSlug), body);
  }

  getBranding(storeSlug: string): Observable<TenantBranding> {
    return this.http.get<TenantBranding>(this.brandingUrl(storeSlug));
  }

  updateBranding(
    storeSlug: string,
    body: Omit<TenantBranding, 'logoUrl' | 'faviconUrl' | 'heroImageUrl'>,
  ): Observable<TenantBranding> {
    return this.csrf.ensureToken().pipe(
      switchMap(() => this.http.put<TenantBranding>(this.brandingUrl(storeSlug), body)),
    );
  }

  uploadBrandingAsset(
    storeSlug: string,
    type: 'logo' | 'favicon' | 'hero',
    file: File,
  ): Observable<TenantBranding> {
    const body = new FormData();
    body.append('file', file);
    return this.csrf.ensureToken().pipe(
      switchMap(() =>
        this.http.put<TenantBranding>(`${this.brandingUrl(storeSlug)}/assets/${type}`, body),
      ),
    );
  }

  deleteBrandingAsset(
    storeSlug: string,
    type: 'logo' | 'favicon' | 'hero',
  ): Observable<TenantBranding> {
    return this.csrf.ensureToken().pipe(
      switchMap(() =>
        this.http.delete<TenantBranding>(`${this.brandingUrl(storeSlug)}/assets/${type}`),
      ),
    );
  }

  private url(storeSlug: string): string {
    return `/api/v1/stores/${encodeURIComponent(storeSlug)}/admin/settings`;
  }

  private brandingUrl(storeSlug: string): string {
    return `/api/v1/stores/${encodeURIComponent(storeSlug)}/admin/branding`;
  }
}
