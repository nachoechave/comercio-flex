import { Injectable } from '@angular/core';

import { PaymentRecovery } from './payment.models';

const STORAGE_PREFIX = 'comercio-flex:payment-recovery:v1';

@Injectable({ providedIn: 'root' })
export class PaymentRecoveryService {
  remember(
    storeSlug: string,
    orderId: string,
    lookupToken: string,
    idempotencyKey: string = globalThis.crypto.randomUUID(),
  ): PaymentRecovery {
    const recovery = { lookupToken, idempotencyKey };
    sessionStorage.setItem(this.key(storeSlug, orderId), JSON.stringify(recovery));
    return recovery;
  }

  find(storeSlug: string, orderId: string): PaymentRecovery | null {
    const raw = sessionStorage.getItem(this.key(storeSlug, orderId));
    if (!raw) return null;
    try {
      const parsed: unknown = JSON.parse(raw);
      if (
        typeof parsed === 'object' &&
        parsed !== null &&
        'lookupToken' in parsed &&
        'idempotencyKey' in parsed &&
        typeof parsed.lookupToken === 'string' &&
        typeof parsed.idempotencyKey === 'string'
      ) {
        return { lookupToken: parsed.lookupToken, idempotencyKey: parsed.idempotencyKey };
      }
    } catch {
      // Invalid browser state is discarded below.
    }
    this.clear(storeSlug, orderId);
    return null;
  }

  rotateAttempt(storeSlug: string, orderId: string): PaymentRecovery | null {
    const current = this.find(storeSlug, orderId);
    return current
      ? this.remember(storeSlug, orderId, current.lookupToken, globalThis.crypto.randomUUID())
      : null;
  }

  clear(storeSlug: string, orderId: string): void {
    sessionStorage.removeItem(this.key(storeSlug, orderId));
  }

  private key(storeSlug: string, orderId: string): string {
    return `${STORAGE_PREFIX}:${storeSlug}:${orderId}`;
  }
}
