import { VariantOptionValue } from '../../shared/variant-options';
import { StorefrontTemplate } from './storefront-template';

export type { StorefrontTemplate } from './storefront-template';

export interface StoreSettings {
  slug: string;
  storeName: string;
  currencyCode: string;
  timezone: string;
  contactPhone?: string | null;
  contactEmail?: string | null;
  pickupAddress?: string | null;
  pickupInstructions?: string | null;
  bankTransferEnabled?: boolean;
  bankTransferDiscountPercentage?: number;
  brandTheme?: BrandTheme;
  branding?: TenantBranding;
}

export interface StorefrontTenantResolution {
  storeSlug: string;
  displayName: string;
}

export type BrandTheme = 'VIOLET' | 'BURGUNDY' | 'FOREST' | 'NAVY';

export type BrandFont = 'SYSTEM' | 'SANS' | 'SERIF';

export interface TenantBranding {
  primaryColor: string;
  secondaryColor: string;
  backgroundColor: string;
  textColor: string;
  font: BrandFont;
  heroTitle: string | null;
  heroSubtitle: string | null;
  template: StorefrontTemplate;
  logoUrl: string | null;
  faviconUrl: string | null;
  heroImageUrl: string | null;
}

export interface PublicCategory {
  id: string;
  name: string;
  slug: string;
  image: PublicProductImage | null;
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
  options?: VariantOptionValue[];
  available: boolean;
  availableQuantity: string;  
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

export type OrderPaymentMethod = 'MERCADO_PAGO' | 'BANK_TRANSFER';

export interface CreateGuestOrder {
  customerName: string;
  customerPhone: string;
  customerEmail: string;
  notes?: string;
  paymentMethod: OrderPaymentMethod;
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
  options?: VariantOptionValue[];
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
  paymentMethod: OrderPaymentMethod;
  customerName: string;
  contactHint: string;
  currencyCode: string;
  listSubtotal: string;
  discountPercentage: string;
  discountAmount: string;
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
