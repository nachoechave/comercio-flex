import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PickupBranch } from './pickup-branch';

@Injectable({ providedIn: 'root' })
export class PickupBranchApi {
  private readonly http = inject(HttpClient);

  list(storeSlug: string): Observable<PickupBranch[]> {
    return this.http.get<PickupBranch[]>(
      `/api/v1/stores/${encodeURIComponent(storeSlug)}/shipping/pickup-branches`,
    );
  }
}
