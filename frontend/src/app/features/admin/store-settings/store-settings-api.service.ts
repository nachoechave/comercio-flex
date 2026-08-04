import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { StoreSettings, UpdateStoreSettings } from './store-settings.models';

@Injectable({ providedIn: 'root' })
export class StoreSettingsApiService {
  private readonly http = inject(HttpClient);

  get(storeSlug: string): Observable<StoreSettings> {
    return this.http.get<StoreSettings>(this.url(storeSlug));
  }

  update(storeSlug: string, body: UpdateStoreSettings): Observable<StoreSettings> {
    return this.http.put<StoreSettings>(this.url(storeSlug), body);
  }

  private url(storeSlug: string): string {
    return `/api/v1/stores/${encodeURIComponent(storeSlug)}/admin/settings`;
  }
}
