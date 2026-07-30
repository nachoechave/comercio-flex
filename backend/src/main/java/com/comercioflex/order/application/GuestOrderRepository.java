package com.comercioflex.order.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.comercioflex.order.domain.GuestOrder;

public interface GuestOrderRepository {

	Optional<StoredGuestOrder> findByIdempotencyKey(UUID idempotencyKey);

	Optional<LockedOrderVariant> lockVariant(UUID variantId);

	String findCurrencyCode();

	long insertOrder(
		UUID orderId,
		UUID idempotencyKey,
		byte[] requestFingerprint,
		byte[] lookupTokenHash,
		String customerName,
		String customerPhone,
		String customerEmail,
		String notes,
		String currencyCode,
			BigDecimal subtotal,
			Instant reservationExpiresAt);

	void insertInitialHistory(long orderInternalId);

	void insertItemsAndReservations(
		long orderInternalId,
		List<ReservedOrderItem> items,
		Instant reservationExpiresAt);

	GuestOrder findByInternalId(long orderInternalId);

	Optional<GuestOrder> findByPublicIdAndTokenHash(UUID orderId, byte[] lookupTokenHash);

	void expireOrder(long orderInternalId);
}

