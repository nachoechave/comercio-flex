import { inject, Injectable } from '@angular/core';
import { StorefrontRoutingService } from '../storefront/storefront-routing.service';

@Injectable({ providedIn: 'root' })
export class RadioRoutingService {
  private readonly storefront = inject(StorefrontRoutingService);
  route(slug: string, ...segments: string[]): string[] {
    const parts = segments.map(part => part === 'ingresar' ? 'login' : part);
    return this.storefront.route(slug)[0] === '/' ? ['/', ...parts] : ['/', slug, ...parts];
  }
}
