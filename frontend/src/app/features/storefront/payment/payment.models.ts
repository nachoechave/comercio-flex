import { GuestOrderStatus } from '../storefront.models';

export type PublicPaymentStatus =
  | 'CREATED'
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'REQUIRES_REVIEW';

export interface CheckoutProStart {
  checkoutUrl: string;
  paymentAttemptId: string;
  expiresAt: string;
  replayed: boolean;
}

export interface PaymentReturnStatus {
  orderId: string;
  orderNumber: string;
  orderStatus: GuestOrderStatus;
  paymentStatus: PublicPaymentStatus;
  returnOutcome: 'PAYMENT_NOT_RECORDED' | null;
  canRetry: boolean;
  updatedAt: string;
}

export interface PaymentRecovery {
  lookupToken: string;
  idempotencyKey: string;
}
