import { PublicProductDetail, PublicProductVariant } from '../storefront.models';
import { VariantOptionValue } from '../../../shared/variant-options';

export type CartLineStatus = 'AVAILABLE' | 'UNAVAILABLE' | 'UNKNOWN';

export interface CartLine {
  productId: string;
  productSlug: string;
  productName: string;
  imageThumbnailUrl: string | null;
  imageAltText: string | null;
  variantId: string;
  size: string | null;
  color: string | null;
  options: VariantOptionValue[];
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

export interface StoredCartLine extends Omit<
  CartLine,
  'status' | 'notice' | 'imageThumbnailUrl' | 'imageAltText' | 'options'
> {
  imageThumbnailUrl?: string | null;
  imageAltText?: string | null;
  options?: VariantOptionValue[];
}
