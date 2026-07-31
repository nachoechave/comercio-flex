import { Injectable } from '@angular/core';

const CHECKOUT_PRO_HOSTS = new Set([
  'www.mercadopago.com',
  'www.mercadopago.com.ar',
  'sandbox.mercadopago.com',
  'sandbox.mercadopago.com.ar',
]);

@Injectable({ providedIn: 'root' })
export class CheckoutProNavigationService {
  navigate(checkoutUrl: string): void {
    const destination = new URL(checkoutUrl);
    if (
      destination.protocol !== 'https:' ||
      !CHECKOUT_PRO_HOSTS.has(destination.hostname.toLowerCase())
    ) {
      throw new Error('Unexpected Checkout Pro destination.');
    }
    globalThis.location.assign(destination.toString());
  }
}
