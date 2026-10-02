import { pickupFulfillmentLabel } from './pickup-fulfillment-label';

describe('pickupFulfillmentLabel', () => {
  it('uses pickup-specific wording without changing persisted statuses', () => {
    expect(pickupFulfillmentLabel('PENDING')).toBe('Pendiente');
    expect(pickupFulfillmentLabel('PREPARING')).toBe('Preparando');
    expect(pickupFulfillmentLabel('SHIPPED')).toBe('Listo para retirar');
    expect(pickupFulfillmentLabel('DELIVERED')).toBe('Entregado');
    expect(pickupFulfillmentLabel('CANCELLED')).toBe('Cancelado');
  });
});
