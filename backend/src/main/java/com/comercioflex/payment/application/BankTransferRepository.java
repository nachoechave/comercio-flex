package com.comercioflex.payment.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankTransferRepository {
	BankTransferConfiguration findConfiguration();
	Optional<BankTransferOrder> lockOrder(UUID orderId, byte[] lookupTokenHash);
	boolean hasBlockingCheckout(long orderInternalId);
	Optional<BankTransferPayment> findCurrentForOrder(long orderInternalId);
	int nextAttemptNumber(long orderInternalId);
	void extendReservation(long orderInternalId, Instant expiresAt);
	void insert(UUID paymentId, long orderInternalId, int attemptNumber);
	Optional<BankTransferPayment> findById(UUID paymentId, boolean forUpdate);
	Optional<BankTransferPayment> findByIdAndOrderToken(
		UUID paymentId, UUID orderId, byte[] lookupTokenHash, boolean forUpdate);
	void attachReceipt(BankTransferPayment payment, String objectKey, String originalFilename,
		String contentType, long size, Instant uploadedAt);
	List<BankTransferPayment> findPendingReview(int limit);
	void approve(BankTransferPayment payment, long reviewerId, Instant reviewedAt);
	void reject(BankTransferPayment payment, long reviewerId, String reason, Instant reviewedAt);
}
