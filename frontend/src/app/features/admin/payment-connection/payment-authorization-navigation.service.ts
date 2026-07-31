import { Injectable } from '@angular/core';

const MERCADO_PAGO_AUTH_HOSTS = new Set(['auth.mercadopago.com', 'auth.mercadopago.com.ar']);

@Injectable({ providedIn: 'root' })
export class PaymentAuthorizationNavigationService {
  navigate(authorizationUrl: string): void {
    const destination = new URL(authorizationUrl);
    if (
      destination.protocol !== 'https:' ||
      !MERCADO_PAGO_AUTH_HOSTS.has(destination.hostname.toLowerCase())
    ) {
      throw new Error('Unexpected payment authorization destination.');
    }
    globalThis.location.assign(destination.toString());
  }
}
