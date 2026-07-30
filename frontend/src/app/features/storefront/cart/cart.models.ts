import { PublicProductDetail, PublicProductVariant } from '../storefront.models';

export type CartLineStatus = 'AVAILABLE' | 'UNAVAILABLE' | 'UNKNOWN';

export interface CartLine {
  productId: string;
  productSlug: string;
  productName: string;
  variantId: string;
  size: string | null;
  color: string | null;
  unitPrice: string;
  quantity: number;
  status: CartLineStatus;
  notice: string | null;
}

export interface AddCartItem {
  product: PublicProductDetail;
  variant: PublicProductVariant;
  quantity: number;
}

export interface AddCartResult {
  quantity: number;
  reachedLimit: boolean;
}

export interface StoredCart {
  version: 1;
  items: StoredCartLine[];
}

export type StoredCartLine = Omit<CartLine, 'status' | 'notice'>;
