import { StoreSettings } from '../../storefront/storefront.models';

export type { StoreSettings };

export interface AdminStoreSettings extends StoreSettings {
  bankName: string | null;
  bankAccountHolder: string | null;
  bankAlias: string | null;
  bankCbuCvu: string | null;
}

export interface UpdateStoreSettings {
  storeName: string;
  contactPhone: string;
  contactEmail: string;
  pickupAddress: string;
  pickupInstructions: string;
  bankTransferEnabled: boolean;
  bankTransferDiscountPercentage: number;
  bankName: string;
  bankAccountHolder: string;
  bankAlias: string;
  bankCbuCvu: string;
}