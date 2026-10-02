export type PickupFulfillmentStatus =
  | 'PENDING'
  | 'PREPARING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED';

export function pickupFulfillmentLabel(status: PickupFulfillmentStatus): string {
  switch (status) {
    case 'PENDING':
      return 'Pendiente';
    case 'PREPARING':
      return 'Preparando';
    case 'SHIPPED':
      return 'Listo para retirar';
    case 'DELIVERED':
      return 'Entregado';
    case 'CANCELLED':
      return 'Cancelado';
  }
}
