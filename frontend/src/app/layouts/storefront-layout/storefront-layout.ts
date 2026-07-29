import { Component, effect, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';

import { StorefrontApiService } from '../../features/storefront/storefront-api.service';
import { StorefrontContextService } from '../../features/storefront/storefront-context.service';

@Component({
  selector: 'app-storefront-layout',
  imports: [RouterLink, RouterOutlet],
  providers: [StorefrontApiService, StorefrontContextService],
  templateUrl: './storefront-layout.html',
  styleUrl: './storefront-layout.scss',
})
export class StorefrontLayout {
  private readonly route = inject(ActivatedRoute);
  protected readonly context = inject(StorefrontContextService);
  protected readonly storeSlug = toSignal(
    this.route.paramMap.pipe(map((params) => params.get('storeSlug') ?? '')),
    { initialValue: this.route.snapshot.paramMap.get('storeSlug') ?? '' },
  );

  constructor() {
    effect(() => {
      const slug = this.storeSlug();
      if (slug) this.context.load(slug);
    });
  }
}
