import { TestBed } from '@angular/core/testing';

import { GuestOrder } from '../storefront.models';
import { GuestOrderHistoryService } from './guest-order-history.service';

describe('GuestOrderHistoryService', () => {
  let service: GuestOrderHistoryService;
  const order: GuestOrder = {
    id: '11111111-1111-4111-8111-111111111111', number: 'ORD-000011',
    status: 'PENDING_CONFIRMATION', fulfillmentType: 'PICKUP', customerName: 'Ana',
    contactHint: 'a***@mail.com', currencyCode: 'ARS', subtotal: '19999.00',
    reservationExpiresAt: '2026-08-26T12:00:00Z', createdAt: '2026-08-25T12:00:00Z', items: [],
    paymentMethod: 'MERCADO_PAGO',
    listSubtotal: '2500.00',
    discountPercentage: '0.00',
    discountAmount: '0.00',
  };

  beforeEach(() => {
    localStorage.clear();
    service = TestBed.inject(GuestOrderHistoryService);
  });

  afterEach(() => localStorage.clear());

  it('persists recovery data across service instances and isolates stores', () => {
    service.remember('tienda-a', order, 'A'.repeat(43));
    const reopened = new GuestOrderHistoryService();

    expect(reopened.list('tienda-a')[0]).toEqual(expect.objectContaining({
      orderId: order.id, orderNumber: 'ORD-000011', lookupToken: 'A'.repeat(43),
    }));
    expect(reopened.list('tienda-b')).toEqual([]);
  });

  it('updates authoritative snapshots and removes invalid tokens', () => {
    service.remember('tienda-a', order, 'A'.repeat(43));
    service.update('tienda-a', { ...order, status: 'CONFIRMED' });
    expect(service.find('tienda-a', order.id)?.lastKnownStatus).toBe('CONFIRMED');

    service.remove('tienda-a', order.id);
    expect(service.list('tienda-a')).toEqual([]);
  });
});
