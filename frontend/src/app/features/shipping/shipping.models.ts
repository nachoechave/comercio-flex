export type ShippingType = 'PICKUP' | 'FIXED_RATE' | 'LOCATION_RATE' | 'POSTAL_CODE_RATE';
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
}
export interface ShippingSnapshot {
  methodId: string;
  name: string;
  type: ShippingType;
  cost: string;
  pickupAddress: string | null;
  instructions: string | null;
  address: ShippingAddress | null;
}
export interface ShippingMethod {
  id: string | null;
  name: string;
  description: string | null;
  type: ShippingType;
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
export type ShippingStatus = 'PENDING' | 'PREPARING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';
export interface Shipment {
  id: string;
  orderId: string;
  status: ShippingStatus;
  carrierName: string | null;
  trackingNumber: string | null;
  trackingUrl: string | null;
  notes: string | null;
  version: number;
}
