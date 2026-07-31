import { TestBed } from '@angular/core/testing';

import { PaymentRecoveryService } from './payment-recovery.service';

describe('PaymentRecoveryService', () => {
  let service: PaymentRecoveryService;

  beforeEach(() => {
    sessionStorage.clear();
    service = TestBed.inject(PaymentRecoveryService);
  });

  afterEach(() => sessionStorage.clear());

  it('keeps private recovery data scoped by store and order', () => {
    service.remember('tienda-a', 'order-1', 'private-token', 'idem-1');

    expect(service.find('tienda-a', 'order-1')).toEqual({
      lookupToken: 'private-token',
      idempotencyKey: 'idem-1',
    });
    expect(service.find('tienda-b', 'order-1')).toBeNull();
  });

  it('discards malformed browser state', () => {
    sessionStorage.setItem('comercio-flex:payment-recovery:v1:tienda-a:order-1', '{bad');

    expect(service.find('tienda-a', 'order-1')).toBeNull();
    expect(sessionStorage.length).toBe(0);
  });
});
