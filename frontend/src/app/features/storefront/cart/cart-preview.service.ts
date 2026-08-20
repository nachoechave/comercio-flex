import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class CartPreviewService {
  private readonly activeStore = signal<string | null>(null);

  readonly storeSlug = this.activeStore.asReadonly();

  open(storeSlug: string): void {
    const slug = storeSlug.trim().toLowerCase();
    if (slug) this.activeStore.set(slug);
  }

  close(): void {
    this.activeStore.set(null);
  }
}
