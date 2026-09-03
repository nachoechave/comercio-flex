import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import {
  catchError,
  distinctUntilChanged,
  map,
  Observable,
  of,
  switchMap,
  tap,
  throwError,
} from 'rxjs';

import { inheritedRouteParam } from '../../core/routing/inherited-route-param';
import { StorefrontApiService } from './storefront-api.service';
import { StorefrontTenantResolution } from './storefront.models';

type StorefrontHostMode = 'unknown' | 'platform' | 'custom-domain';

@Injectable({
  providedIn: 'root',
})
export class StorefrontRoutingService {
  private readonly api = inject(StorefrontApiService);

  private readonly hostMode = signal<StorefrontHostMode>('unknown');
  private readonly domainResolution = signal<StorefrontTenantResolution | null>(null);

  resolveCustomDomain(): Observable<boolean> {
    const currentMode = this.hostMode();

    if (currentMode === 'custom-domain') {
      return of(true);
    }

    if (currentMode === 'platform') {
      return of(false);
    }

    return this.api.resolveStorefront().pipe(
      tap((resolution) => {
        this.domainResolution.set(resolution);
        this.hostMode.set('custom-domain');
      }),
      map(() => true),
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.domainResolution.set(null);
          this.hostMode.set('platform');
          return of(false);
        }

        return throwError(() => error);
      }),
    );
  }

  storeSlug(route: ActivatedRoute): Observable<string | null> {
    return inheritedRouteParam(route, 'storeSlug').pipe(
      switchMap((routeSlug) => {
        if (routeSlug) {
          return of(routeSlug);
        }

        const resolvedSlug = this.domainResolution()?.storeSlug;

        if (resolvedSlug) {
          return of(resolvedSlug);
        }

        return this.resolveCustomDomain().pipe(
          map((customDomain) =>
            customDomain ? (this.domainResolution()?.storeSlug ?? null) : null,
          ),
        );
      }),
      distinctUntilChanged(),
    );
  }

  route(storeSlug: string, ...segments: string[]): string[] {
    if (this.hostMode() === 'custom-domain') {
      return ['/', ...segments];
    }

    return ['/tiendas', storeSlug, ...segments];
  }
}