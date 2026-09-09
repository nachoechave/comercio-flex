import { inject, Injectable } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { StorefrontRoutingService } from '../storefront/storefront-routing.service';

@Injectable()
export class RadioContext {
  private readonly routing = inject(StorefrontRoutingService);
  readonly slug = toSignal(this.routing.storeSlug(inject(ActivatedRoute)), { initialValue: null });
  link(...segments: string[]) { return this.routing.route(this.slug() ?? '', ...segments); }
}
