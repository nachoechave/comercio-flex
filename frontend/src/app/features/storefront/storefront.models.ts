export interface StoreSettings {
  slug: string;
  storeName: string;
  currencyCode: string;
  timezone: string;
  contactPhone?: string | null;
  contactEmail?: string | null;
  pickupAddress?: string | null;
  pickupInstructions?: string | null;
  brandTheme?: BrandTheme;
}

export type BrandTheme = 'VIOLET' | 'BURGUNDY' | 'FOREST' | 'NAVY';

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

export interface PublicProductImage {
  id: string;
  url: string;
  thumbnailUrl: string;
  altText: string;
  width?: number;
  height?: number;
  updatedAt?: string;
}

export interface PublicProductSummary {
  id: string;
  name: string;
  slug: string;
  category: PublicProductCategory;
  priceFrom: string;
  priceTo: string;
  available: boolean;
  image: PublicProductImage | null;
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
  image: PublicProductImage | null;
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
  customerEmail: string;
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

export type GuestOrderStatus =
  | 'PENDING_CONFIRMATION'
  | 'CONFIRMED'
  | 'READY_FOR_PICKUP'
  | 'COMPLETED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'EXPIRED';

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
