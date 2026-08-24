import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { AdminStoreSettings, UpdateStoreSettings } from './store-settings.models';

@Injectable({ providedIn: 'root' })
export class StoreSettingsApiService {
  private readonly http = inject(HttpClient);

  get(storeSlug: string): Observable<AdminStoreSettings> {
    return this.http.get<AdminStoreSettings>(this.url(storeSlug));
  }

  update(storeSlug: string, body: UpdateStoreSettings): Observable<AdminStoreSettings> {
    return this.http.put<AdminStoreSettings>(this.url(storeSlug), body);
  }

  private url(storeSlug: string): string {
    return `/api/v1/stores/${encodeURIComponent(storeSlug)}/admin/settings`;
  }
}
