import { StoreSettings } from '../../storefront/storefront.models';

export type { StoreSettings };

export interface UpdateStoreSettings {
  storeName: string;
  contactPhone: string;
  contactEmail: string;
  pickupAddress: string;
  pickupInstructions: string;
}
