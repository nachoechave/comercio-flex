import { ProductStatus } from '../products/product.models';

export type InventoryAvailability = 'ALL' | 'IN_STOCK' | 'OUT_OF_STOCK';
export type AdjustmentDirection = 'INCREASE' | 'DECREASE';
export type AdjustmentReason = 'RECEIPT' | 'CORRECTION' | 'DAMAGE' | 'RETURN' | 'OTHER';
export type InventoryMovementReason = AdjustmentReason | 'ORDER_CONFIRMED' | 'ORDER_CANCELLED';

export interface InventoryItem {
  variantId: string;
  productId: string;
  productName: string;
  productStatus: ProductStatus;
  sku: string;
  size: string | null;
  color: string | null;
  variantActive: boolean;
  quantity: string;
  version: number;
  updatedAt: string;
}

export interface InventoryPage {
  items: InventoryItem[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface MovementActor {
  id: string;
  displayName: string;
}

export interface InventoryMovement {
  id: string;
  direction: AdjustmentDirection;
  delta: string;
  quantityBefore: string;
  quantityAfter: string;
  reason: InventoryMovementReason;
  note: string | null;
  actor: MovementActor;
  createdAt: string;
}

export interface MovementPage {
  items: InventoryMovement[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface StockAdjustment {
  direction: AdjustmentDirection;
  quantity: string;
  reason: AdjustmentReason;
  note?: string;
}

export interface AdjustmentResponse {
  inventory: InventoryItem;
  movement: InventoryMovement;
}
