import { VariantOptionValue } from '../../../shared/variant-options';

export type ProductStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface ProductImage {
  id: string;
  url: string;
  thumbnailUrl: string;
  altText: string;
  width?: number;
  height?: number;
  updatedAt?: string;
}

export interface ProductCategory {
  id: string;
  name: string;
  active: boolean;
}

export interface ProductVariant {
  id: string;
  sku: string;
  price: string;
  size: string | null;
  color: string | null;
  options?: VariantOptionValue[];
  active: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProductDetail {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  status: ProductStatus;
  category: ProductCategory;
  variants: ProductVariant[];
  image: ProductImage | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProductSummary {
  id: string;
  name: string;
  slug: string;
  status: ProductStatus;
  category: ProductCategory;
  variantCount: number;
  activeVariantCount: number;
  priceFrom: string | null;
  priceTo: string | null;
  image: ProductImage | null;
  version: number;
  updatedAt: string;
}

export interface ProductPage {
  items: ProductSummary[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface SaveVariant {
  sku: string;
  price: string;
  size?: string;
  color?: string;
  options?: VariantOptionValue[];
}

export interface CreateProduct {
  name: string;
  description?: string;
  categoryId: string;
  variants: SaveVariant[];
}

export interface UpdateProduct {
  name: string;
  description?: string;
  categoryId: string;
  version: number;
}

export interface ProductQuery {
  page: number;
  size: number;
  query?: string;
  status?: ProductStatus;
  categoryId?: string;
}

export interface UpdateVariant extends SaveVariant {
  version: number;
}
