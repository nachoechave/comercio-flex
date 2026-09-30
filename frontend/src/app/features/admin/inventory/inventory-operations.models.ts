export interface InventoryTransfer {
  id: string;
  fromBranchId: string;
  fromBranchName: string;
  toBranchId: string;
  toBranchName: string;
  itemCount: number;
  totalUnits: string;
  actorDisplayName: string;
  note: string | null;
  createdAt: string;
}

export interface InventoryTransferPayload {
  fromBranchId: string;
  toBranchId: string;
  items: Array<{ variantId: string; quantity: string }>;
  note?: string | null;
}

export interface OperationalInventoryMovement {
  id: string;
  variantId: string;
  productName: string;
  sku: string;
  branchId: string | null;
  branchName: string | null;
  direction: 'INCREASE' | 'DECREASE';
  delta: string;
  reason:
    | 'RECEIPT'
    | 'CORRECTION'
    | 'DAMAGE'
    | 'RETURN'
    | 'OTHER'
    | 'ORDER_CONFIRMED'
    | 'ORDER_CANCELLED'
    | 'LOCAL_SALE'
    | 'TRANSFER_OUT'
    | 'TRANSFER_IN';
  note: string | null;
  actorDisplayName: string;
  createdAt: string;
}

export interface LowStockAlert {
  branchId: string;
  branchName: string;
  variantId: string;
  productName: string;
  sku: string;
  quantity: string;
}

export interface LowStockSummary {
  threshold: string;
  alerts: LowStockAlert[];
}
