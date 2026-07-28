import { ActivatedRoute } from '@angular/router';
import { combineLatest, distinctUntilChanged, map, Observable, of } from 'rxjs';

import { routeParam } from '../../../core/auth/auth.guards';

export function inheritedRouteParam(
  route: ActivatedRoute,
  name: string,
): Observable<string | null> {
  if (!route.pathFromRoot?.length) {
    return of(routeParam(route.snapshot, name));
  }

  return combineLatest(route.pathFromRoot.map((segment) => segment.paramMap)).pipe(
    map((parameterMaps) => {
      for (let index = parameterMaps.length - 1; index >= 0; index -= 1) {
        const value = parameterMaps[index].get(name);
        if (value) {
          return value;
        }
      }
      return null;
    }),
    distinctUntilChanged(),
  );
}
