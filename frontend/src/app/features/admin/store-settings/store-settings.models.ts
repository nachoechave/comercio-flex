import { BrandTheme, StoreSettings } from '../../storefront/storefront.models';

export type { BrandTheme, StoreSettings };

export interface UpdateStoreSettings {
  storeName: string;
  contactPhone: string;
  contactEmail: string;
  pickupAddress: string;
  pickupInstructions: string;
  brandTheme: BrandTheme;
}
