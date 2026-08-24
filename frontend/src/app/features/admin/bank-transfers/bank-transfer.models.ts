export type BankTransferStatus =
  | 'AWAITING_RECEIPT'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'REJECTED';

export interface AdminBankTransferPayment {
  id: string;
  orderId: string;
  orderNumber: string;
  customerName: string;
  amount: string;
  currencyCode: string;
  attemptNumber: number;
  status: BankTransferStatus;
  receiptAvailable: boolean;
  receiptOriginalFilename: string | null;
  receiptContentType: string | null;
  receiptSize: number | null;
  receiptUploadedAt: string | null;
  reservationExpiresAt: string;
  reviewedAt: string | null;
  rejectionReason: string | null;
  createdAt: string;
  updatedAt: string;
}
