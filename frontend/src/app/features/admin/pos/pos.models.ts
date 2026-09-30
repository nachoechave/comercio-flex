export type PosPaymentMethod = 'CASH' | 'BANK_TRANSFER' | 'CARD' | 'OTHER';

export interface PosCatalogItem {
  variantId: string;
  productName: string;
  sku: string;
  size: string | null;
  color: string | null;
  unitPrice: string;
  availableQuantity: string;
}

export interface PosSaleLinePayload {
  variantId: string;
  quantity: string;
}

export interface PosSalePayload {
  branchId: string;
  paymentMethod: PosPaymentMethod;
  items: PosSaleLinePayload[];
}

export interface PosSaleItem {
  variantId: string;
  productName: string;
  sku: string;
  size: string | null;
  color: string | null;
  unitPrice: string;
  quantity: string;
  lineTotal: string;
}

export interface PosSale {
  id: string;
  branchId: string;
  branchName: string;
  sellerDisplayName: string;
  paymentMethod: PosPaymentMethod;
  currencyCode: string;
  subtotal: string;
  items: PosSaleItem[];
  createdAt: string;
}
