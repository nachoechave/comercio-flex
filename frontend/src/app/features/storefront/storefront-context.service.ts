import { HttpErrorResponse } from '@angular/common/http';
import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { Subscription } from 'rxjs';

import { StorefrontApiService } from './storefront-api.service';
import { storefrontErrorMessage } from './storefront-errors';
import { StoreSettings } from './storefront.models';

@Injectable()
export class StorefrontContextService {
  private readonly api = inject(StorefrontApiService);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;
  private requestedSlug: string | null = null;

  readonly settings = signal<StoreSettings | null>(null);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly notFound = signal(false);
  readonly currencyCode = computed(() => this.settings()?.currencyCode ?? 'ARS');

  constructor() {
    this.destroyRef.onDestroy(() => this.request?.unsubscribe());
  }

  load(storeSlug: string, force = false): void {
    if (!force && this.requestedSlug === storeSlug && (this.loading() || this.settings())) return;

    this.request?.unsubscribe();
    this.requestedSlug = storeSlug;
    this.settings.set(null);
    this.errorMessage.set(null);
    this.notFound.set(false);
    this.loading.set(true);

    this.request = this.api.getSettings(storeSlug).subscribe({
      next: (settings) => {
        if (this.requestedSlug !== storeSlug) return;
        this.settings.set(settings);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        if (this.requestedSlug !== storeSlug) return;
        this.notFound.set(error instanceof HttpErrorResponse && error.status === 404);
        this.errorMessage.set(
          storefrontErrorMessage(error, 'No pudimos cargar la información de esta tienda.'),
        );
        this.loading.set(false);
      },
    });
  }

  retry(): void {
    if (this.requestedSlug) this.load(this.requestedSlug, true);
  }
}
