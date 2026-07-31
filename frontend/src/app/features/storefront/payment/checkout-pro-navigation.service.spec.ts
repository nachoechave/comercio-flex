import { TestBed } from '@angular/core/testing';

import { CheckoutProNavigationService } from './checkout-pro-navigation.service';

describe('CheckoutProNavigationService', () => {
  const service = TestBed.inject(CheckoutProNavigationService);

  it('rejects non-HTTPS checkout destinations', () => {
    expect(() => service.navigate('http://www.mercadopago.com.ar/checkout')).toThrow();
  });

  it('rejects lookalike and unrelated hosts', () => {
    expect(() => service.navigate('https://www.mercadopago.com.ar.evil.test/checkout')).toThrow();
    expect(() => service.navigate('https://example.com/checkout')).toThrow();
  });
});
