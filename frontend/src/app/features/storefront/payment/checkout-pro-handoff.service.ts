import { Injectable } from '@angular/core';

import { CheckoutProStart } from './payment.models';

@Injectable({ providedIn: 'root' })
export class CheckoutProHandoffService {
  private readonly checkouts = new Map<string, CheckoutProStart>();

  remember(storeSlug: string, orderId: string, checkout: CheckoutProStart): void {
    this.checkouts.set(this.key(storeSlug, orderId), checkout);
  }

  find(storeSlug: string, orderId: string): CheckoutProStart | null {
    return this.checkouts.get(this.key(storeSlug, orderId)) ?? null;
  }

  forget(storeSlug: string, orderId: string): void {
    this.checkouts.delete(this.key(storeSlug, orderId));
  }

  private key(storeSlug: string, orderId: string): string {
    return `${storeSlug}:${orderId}`;
  }
}
