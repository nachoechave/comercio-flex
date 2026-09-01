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

export interface QrOrderStart {
  paymentAttemptId: string;
  qrData: string | null;
  expiresAt: string;
  status: PublicPaymentStatus;
  replayed: boolean;
}

export interface PaymentMethods {
  mercadoPago: boolean;
  mercadoPagoQr?: boolean;
  bankTransfer: boolean;
}

export type BankTransferStatus =
  | 'AWAITING_RECEIPT'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'REJECTED';

export interface BankTransferPayment {
  id: string;
  orderId: string;
  orderNumber: string;
  attemptNumber: number;
  status: BankTransferStatus;
  bankName: string | null;
  accountHolder: string;
  alias: string | null;
  cbuCvu: string | null;
  amount: string;
  currencyCode: string;
  reservationExpiresAt: string;
  receiptUploadedAt: string | null;
  rejectionReason: string | null;
  canUpload: boolean;
  updatedAt: string;
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
