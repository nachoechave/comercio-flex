package com.comercioflex.payment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.comercioflex.order.domain.OrderStatus;

public record BankTransferOrder(
	long internalId,
	UUID id,
	long number,
	OrderStatus status,
	String customerName,
	BigDecimal amount,
	String currencyCode,
	Instant reservationExpiresAt
) {
}
