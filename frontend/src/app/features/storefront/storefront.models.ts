export interface StoreSettings {
  slug: string;
  storeName: string;
  currencyCode: string;
  timezone: string;
}

export interface PublicCategory {
  id: string;
  name: string;
  slug: string;
}

export interface PublicProductCategory {
  id: string;
  name: string;
  slug: string;
}

export interface PublicProductSummary {
  id: string;
  name: string;
  slug: string;
  category: PublicProductCategory;
  priceFrom: string;
  priceTo: string;
  available: boolean;
}

export interface PublicProductPage {
  items: PublicProductSummary[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface PublicProductVariant {
  id: string;
  price: string;
  size: string | null;
  color: string | null;
  available: boolean;
}

export interface PublicProductDetail {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  category: PublicProductCategory;
  variants: PublicProductVariant[];
}

export interface PublicProductQuery {
  page: number;
  size: number;
  q?: string;
  category?: string;
}

export interface CreateGuestOrder {
  customerName: string;
  customerPhone: string;
  customerEmail?: string;
  notes?: string;
  items: {
    variantId: string;
    quantity: string;
  }[];
}

export interface GuestOrderItem {
  productId: string;
  variantId: string;
  productName: string;
  size: string | null;
  color: string | null;
  unitCode: 'UNIT';
  unitPrice: string;
  quantity: string;
  lineTotal: string;
}

export type GuestOrderStatus = 'PENDING_CONFIRMATION' | 'EXPIRED';

export interface GuestOrder {
  id: string;
  number: string;
  status: GuestOrderStatus;
  fulfillmentType: 'PICKUP';
  customerName: string;
  contactHint: string;
  currencyCode: string;
  subtotal: string;
  reservationExpiresAt: string;
  createdAt: string;
  items: GuestOrderItem[];
}

export interface CreatedGuestOrder {
  order: GuestOrder;
  lookupToken: string;
  replayed: boolean;
}
