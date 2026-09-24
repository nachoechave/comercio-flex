export type ShippingType =
  | 'PICKUP'
  | 'FIXED_RATE'
  | 'LOCATION_RATE'
  | 'POSTAL_CODE_RATE'
  | 'CARRIER';
export type CarrierDocumentType = 'DNI' | 'CUIT' | 'CUIL';
export type CarrierEnvironment = 'SANDBOX' | 'PRODUCTION';
export interface ShippingAddress {
  street: string;
  number: string;
  apartment?: string;
  city: string;
  province: string;
  postalCode: string;
}
export interface ShippingSelection {
  methodId: string;
  address?: ShippingAddress;
  expectedTotal: string;
  quoteToken?: string;
  documentType?: CarrierDocumentType;
  documentNumber?: string;
}
export interface ShippingQuote {
  methodId: string;
  name: string;
  description: string | null;
  type: ShippingType;
  shippingAmount: string;
  freeShipping: boolean;
  listSubtotal: string;
  discountAmount: string;
  subtotal: string;
  total: string;
  pickupAddress: string | null;
  instructions: string | null;
  provider?: string | null;
  serviceCode?: string | null;
  estimatedDays?: number | null;
  quoteToken?: string | null;
}
export interface CarrierParcel {
  weightGrams: number;
  lengthCm: number;
  widthCm: number;
  heightCm: number;
}
export interface ShippingSnapshot {
  methodId: string;
  name: string;
  type: ShippingType;
  cost: string;
  pickupAddress: string | null;
  instructions: string | null;
  address: ShippingAddress | null;
  provider?: string | null;
  serviceCode?: string | null;
  quoteToken?: string | null;
  documentType?: CarrierDocumentType | null;
  documentNumber?: string | null;
  parcel?: CarrierParcel | null;
  providerCost?: string | null;
}
export interface ShippingMethod {
  id: string | null;
  name: string;
  description: string | null;
  type: Exclude<ShippingType, 'CARRIER'>;
  price: number;
  active: boolean;
  pickupAddress: string | null;
  instructions: string | null;
  rules: { destination: string; price: number }[];
}
export interface ShippingSettings {
  freeShippingThreshold: number | null;
  version: number;
  methods: ShippingMethod[];
}
export interface CarrierOrigin {
  postalCode: string;
  street: string;
  number: string;
  city: string;
  province: string;
  country: string;
  senderName: string;
  senderEmail: string;
  senderPhone: string;
  senderDocumentType: CarrierDocumentType;
  senderDocumentNumber: string;
}
export interface CarrierSettings {
  provider: 'ANDREANI';
  enabled: boolean;
  environment: CarrierEnvironment;
  clientCode: string | null;
  contractCode: string | null;
  credentialsConfigured: boolean;
  origin: CarrierOrigin | null;
  defaultParcel: CarrierParcel | null;
  trackingSyncMinutes: number;
  version: number;
}
export interface CarrierSettingsSave {
  provider: 'ANDREANI';
  enabled: boolean;
  environment: CarrierEnvironment;
  clientCode: string | null;
  contractCode: string | null;
  username: string | null;
  password: string | null;
  clearCredentials: boolean;
  origin: CarrierOrigin | null;
  defaultParcel: CarrierParcel | null;
  trackingSyncMinutes: number;
  version: number;
}
export type ShippingStatus = 'PENDING' | 'PREPARING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';
export interface Shipment {
  id: string;
  orderId: string;
  provider: string | null;
  status: ShippingStatus;
  carrierName: string | null;
  trackingNumber: string | null;
  trackingUrl: string | null;
  shippingCost?: string | null;
  providerCost?: string | null;
  externalReference?: string | null;
  labelReference?: string | null;
  providerStatus?: string | null;
  lastSyncedAt?: string | null;
  providerError?: string | null;
  notes: string | null;
  version: number;
}
