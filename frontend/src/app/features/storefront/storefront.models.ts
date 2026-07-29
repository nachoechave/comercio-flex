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
