import { TestBed } from '@angular/core/testing';

import { CheckoutProHandoffService } from './checkout-pro-handoff.service';

describe('CheckoutProHandoffService', () => {
  const checkout = {
    checkoutUrl: 'https://www.mercadopago.com.ar/checkout',
    paymentAttemptId: 'attempt-1',
    expiresAt: '2026-08-30T18:00:00Z',
    replayed: false,
  };

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({});
  });

  it('keeps checkout data isolated by store and order without browser persistence', () => {
    const service = TestBed.inject(CheckoutProHandoffService);

    service.remember('tienda-a', 'order-1', checkout);

    expect(service.find('tienda-a', 'order-1')).toEqual(checkout);
    expect(service.find('tienda-b', 'order-1')).toBeNull();
    expect(service.find('tienda-a', 'order-2')).toBeNull();
    expect(sessionStorage.length).toBe(0);
    expect(localStorage.length).toBe(0);

    service.forget('tienda-a', 'order-1');
    expect(service.find('tienda-a', 'order-1')).toBeNull();
  });
});
