import { VariantOptionValue } from '../../../shared/variant-options';

export type OrderStatus =
  | 'PENDING_CONFIRMATION'
  | 'CONFIRMED'
  | 'READY_FOR_PICKUP'
  | 'COMPLETED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'EXPIRED';

export interface AdminOrderSummary {
  id: string;
  number: string;
  status: OrderStatus;
  fulfillmentType: 'PICKUP';
  customerName: string;
  customerPhone: string;
  currencyCode: string;
  subtotal: string;
  createdAt: string;
}

export interface AdminOrderPage {
  items: AdminOrderSummary[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface AdminOrderItem {
  productId: string;
  variantId: string;
  productName: string;
  size: string | null;
  color: string | null;
  options?: VariantOptionValue[];
  unitCode: string;
  unitPrice: string;
  quantity: string;
  lineTotal: string;
}

export interface OrderHistory {
  id: string;
  previousStatus: OrderStatus | null;
  newStatus: OrderStatus;
  note: string | null;
  actorId: string | null;
  actorDisplayName: string;
  createdAt: string;
}

export interface AdminOrderDetail extends AdminOrderSummary {
  customerEmail: string | null;
  notes: string | null;
  reservationExpiresAt: string;
  version: number;
  items: AdminOrderItem[];
  history: OrderHistory[];
}

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING_CONFIRMATION: 'Pendiente',
  CONFIRMED: 'Confirmado',
  READY_FOR_PICKUP: 'Listo para retirar',
  COMPLETED: 'Completado',
  REJECTED: 'Rechazado',
  CANCELLED: 'Cancelado',
  EXPIRED: 'Vencido',
};

export const ORDER_ACTION_LABELS: Partial<Record<OrderStatus, string>> = {
  CONFIRMED: 'Confirmar',
  READY_FOR_PICKUP: 'Marcar listo para retirar',
  COMPLETED: 'Completar',
  REJECTED: 'Rechazar',
  CANCELLED: 'Cancelar',
};

export const ORDER_TRANSITIONS: Partial<Record<OrderStatus, OrderStatus[]>> = {
  PENDING_CONFIRMATION: ['CONFIRMED', 'REJECTED'],
  CONFIRMED: ['READY_FOR_PICKUP', 'CANCELLED'],
  READY_FOR_PICKUP: ['COMPLETED', 'CANCELLED'],
};
