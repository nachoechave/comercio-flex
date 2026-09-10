import { inject, Injectable } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { StorefrontRoutingService } from '../storefront/storefront-routing.service';
import { RadioRoutingService } from './radio-routing.service';

@Injectable()
export class RadioContext {
  private readonly routing = inject(StorefrontRoutingService);
  readonly slug = toSignal(this.routing.storeSlug(inject(ActivatedRoute)), { initialValue: null });
  private readonly publicRouting = inject(RadioRoutingService);
  link(...segments: string[]) { return this.publicRouting.route(this.slug() ?? '', ...segments); }
}
